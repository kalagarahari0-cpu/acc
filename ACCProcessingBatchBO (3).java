/**************************************************************************************************************
 * File Name   : ACCProcessingBatchBO
 * Description : This class is the business object class , which processes the part level data for ACC 
 * 				 and keep it ready for use by user later from the screen.
 *				 
 * Roles       :
 * Known Bugs  :
 * Date Created: Feb 12, 2019
 * Created by  : L&T Infotech
 *
 **************************************************************************************************************/
package com.honda.cart2.batch.bo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager; import org.apache.logging.log4j.Logger;
import org.omg.CORBA.portable.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;

import com.honda.cart2.batch.CARTBatchException;
import com.honda.cart2.batch.dao.ACCProcessingBatchDAO;
import com.honda.cart2.batch.dvo.acc.EnterACCApplicationsDVO;
import com.honda.cart2.batch.dvo.acc.EnterACCApplicationsSuppMTOSummaryDVO;
import com.honda.cart2.batch.dvo.acc.EnterACCEventPartDetailsDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppFEMDMTODTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryACCCommentsDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryACCCostDataDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryACCDataDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryACCDataDetailsDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryPartLevelDataDTO;
import com.honda.cart2.batch.entity.AccRuleEnum;
import com.honda.cart2.common.dto.AccDefinitionDto;
import com.honda.cart2.common.dto.EmailNotificationDTO;
import com.honda.cart2.common.dto.EmailUserDTO;
import com.honda.cart2.common.util.BatchConstantsIF;
import com.honda.cart2.common.util.Utility;
import com.honda.cart2.common.util.BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR;

/**
 * @author vcc90520
 *
 */
public class ACCProcessingBatchBO extends EmailNotificationBO {

	protected static final Logger log = LogManager.getLogger(ACCProcessingBatchBO.class);
	private String CLASS_NAME = ACCProcessingBatchBO.class.getName();
	
	//DAO class required for this BO
	@Autowired
	private ACCProcessingBatchDAO accProcessingBatchDAO;
	
	private static ArrayList<String> m_lInHouseSupp = new ArrayList<String>();
	private static String m_strDefaultEffectiveDate; 
	
	//private EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO;
	
	public ACCProcessingBatchDAO getAccProcessingBatchDAO() {
		return accProcessingBatchDAO;
	}
	
	public void setAccProcessingBatchDAO(ACCProcessingBatchDAO accProcessingBatchDAO) {
		this.accProcessingBatchDAO = accProcessingBatchDAO;
	}

	public static String getM_strDefaultEffectiveDate() {
		return m_strDefaultEffectiveDate;
	}

	public static void setM_strDefaultEffectiveDate(String m_strDefaultEffectiveDate) {
		ACCProcessingBatchBO.m_strDefaultEffectiveDate = m_strDefaultEffectiveDate;
	}
	
	
	public String getKIYearStartDate() {
		int startYear = 0;
		String effDate = null;
		int currentYear=0;
		int currentMonth=0;
		
		Calendar now = Calendar.getInstance();
		currentYear =  now.get(Calendar.YEAR);
		currentMonth = now.get(Calendar.MONTH)+1;
		
		if (currentMonth >=1   && currentMonth <=3) {
			startYear = currentYear - 1;				
		} else if  (currentMonth >=4   && currentMonth <=12)  {
			startYear = currentYear;}
		effDate = startYear+"-04-01";
		return effDate;
	}

	/**
	 * This method is the main method called from the service
	 * @throws Exception
	 */
	public void mainAccProcessingBatch() throws Exception{
		log.info("Entered mainAccProcessingBatch() method java test in "+ CLASS_NAME +".");
		
		EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO = null;
		try{
			enterACCApplicationsSuppMTOSummaryDVO = new EnterACCApplicationsSuppMTOSummaryDVO();
			//START
			m_lInHouseSupp = accProcessingBatchDAO.findInHouseSupp();
			
			//get data from the Parameter table based on the the job submitted by user, 1st in the queue
			fetchParamsForProcessing(enterACCApplicationsSuppMTOSummaryDVO);
			
			//Set default effective date to the first of the month(MM/dd/yyyy) based on the current monthly event
			//set default date to start of current KI year for E2 event
			if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().contains("E2")){
				m_strDefaultEffectiveDate = getKIYearStartDate();
			}else{
				m_strDefaultEffectiveDate = enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().substring(4, 6) 
				+"/01/"+ enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().substring(0, 4);
			}
			//Update status to In-Progress
			accProcessingBatchDAO.updateJobStatusInFCASD1(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_RULES_STATUS.INPROGRESS.value(),enterACCApplicationsSuppMTOSummaryDVO);
			
			//get Engine, Mission and Differential for the respective Frame.
			//Use this DTO to create list of FEMD MTO list EnterACCSuppFEMDMTODTO
			fetchMissionEngineDiffBasedOnFrame(enterACCApplicationsSuppMTOSummaryDVO);
			
			//fetch active/enabled rules to be applied 
			accProcessingBatchDAO.findAllActiveRules();
			
			//this method does the complete processing of the data
			loadPartLevelDetailsTabData(enterACCApplicationsSuppMTOSummaryDVO);
			
			//Update status to Success
			accProcessingBatchDAO.updateJobStatusInFCASD1(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_RULES_STATUS.SUCCESS.value(),enterACCApplicationsSuppMTOSummaryDVO);
			
			//Send email notification
			//CPT-1381 commenting out email notification as MPC like to check status in ACC jobs screen
			/*AccDefinitionDto accDefinition = accProcessingBatchDAO.findACCJobDefinitionBySeqNo(enterACCApplicationsSuppMTOSummaryDVO.getSeqNo());
			if (accDefinition != null && accDefinition.getStatus().equalsIgnoreCase(BatchConstantsIF.ACC_RULES_STATUS.SUCCESS.value())) {
				EmailUserDTO emailUser = accProcessingBatchDAO.findUserInfoForEmailNotification(accDefinition.getCreatedBy());
				if (emailUser != null) {
					emailUser.setIntendedas(BatchConstantsIF.EMAIL_INTENDED_AS.ToUser.value());
					sendEmailNotification(emailUser, accDefinition);
				} else {
					log.info("Unable to find user "+accDefinition.getCreatedBy()+" in FCUSR1 table in mainAccProcessingBatch() method of "+ CLASS_NAME);
				}
			} else {
				log.info("Unable to find record with SEQ No: "+enterACCApplicationsSuppMTOSummaryDVO.getSeqNo()+" in FCASD1 table in mainAccProcessingBatch() method of "+ CLASS_NAME);
			}*/
			//CPT-1381 end
		} catch(Exception e){
			log.info("Exception encounetered in mainAccProcessingBatch() method in "+ CLASS_NAME, e);
			log.error("Exception encounetered in mainAccProcessingBatch() method in "+ CLASS_NAME, e);
			e.printStackTrace();
			accProcessingBatchDAO.updateJobStatusInFCASD1(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_RULES_STATUS.JOB_FAILED.value(),enterACCApplicationsSuppMTOSummaryDVO);
	        throw new CARTBatchException(e);
		}
		log.info("Exiting mainAccProcessingBatch() method in "+ CLASS_NAME +".");
	}
	
	private void fetchParamsForProcessing(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO){
		log.info("Entered fetchParamsForProcessing() method in "+ CLASS_NAME +".");
		
		List<Map<String, Object>> results = accProcessingBatchDAO.fetchParamsForProcessing();
		ArrayList<EnterACCApplicationsDVO> m_lstCurrentApplications = null;
		ArrayList<EnterACCApplicationsDVO> m_lstBaseApplications = null;
		boolean isFirstIteration = true;
		
		if(null!=results && !results.isEmpty()){
			m_lstCurrentApplications = new ArrayList<EnterACCApplicationsDVO>();
			m_lstBaseApplications = new ArrayList<EnterACCApplicationsDVO>();
			EnterACCApplicationsDVO currentApplicationsDVO = null;
			EnterACCApplicationsDVO baseApplicationsDVO = null;
			for(Map map:results){
				
				if(isFirstIteration){// Common data to be set only once
					isFirstIteration = false;
					enterACCApplicationsSuppMTOSummaryDVO.setSeqNo((BigDecimal)(map.get("SEQ_NO")));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strComparedEvents(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("COMPARED_EVENTS")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strBaseEvent(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("BASE_EVENT_NAME")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strBaseEventRev(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("BASE_EVENT_REV_NO")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strCurrentEvent(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURRENT_EVENT_NAME")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strCurrentEventRev(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURRENT_EVENT_REV")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strCurrency(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURRENCY")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strProcGroupFrom(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("PROC_GROUP_FROM")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strProcGroupTo(
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("PROC_GROUP_TO")))));
					enterACCApplicationsSuppMTOSummaryDVO.setCreatedTstp((Timestamp)map.get("CREATED_TSTP"));
					
					//Budget Control number no required
					/*enterACCApplicationsSuppMTOSummaryDVO.setM_strBudgetControlNos(//TODO Change this to current budget 
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("ACC_PARM1_TEXT")))));
					enterACCApplicationsSuppMTOSummaryDVO.setM_strBudgetControlNos(//TODO Change this to BASE budget 
							Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("ACC_PARM2_TEXT")))));*/
				}
				baseApplicationsDVO = new EnterACCApplicationsDVO();
				baseApplicationsDVO.setBudgetControlNumber(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("BASE_BUDGET_CTRL_NO")))));
				baseApplicationsDVO.setTargetModel(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("BASE_TGT_MODEL_DEV_CODE")))));
				baseApplicationsDVO.setType(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("BASE_MTC_TYPE")))));
				baseApplicationsDVO.setOption(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("BASE_MTC_OPTION")))));
				m_lstBaseApplications.add(baseApplicationsDVO);
				
				currentApplicationsDVO = new EnterACCApplicationsDVO();
				currentApplicationsDVO.setBudgetControlNumber(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURR_BUDGET_CTRL_NO")))));
				currentApplicationsDVO.setTargetModel(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURR_TGT_MODEL_DEV_CODE")))));
				currentApplicationsDVO.setType(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURR_MTC_TYPE")))));
				currentApplicationsDVO.setOption(
						Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CURR_MTC_OPTION")))));
				m_lstCurrentApplications.add(currentApplicationsDVO);
						
			}
			enterACCApplicationsSuppMTOSummaryDVO.setM_lCurrentApplications(m_lstCurrentApplications);
			enterACCApplicationsSuppMTOSummaryDVO.setM_lBaseApplications(m_lstBaseApplications);
			//enterACCApplicationsSuppMTOSummaryDVO.setM_strDataToDisplay(enterACCRulesDVO.getM_strDataToDisplay());
		}
		log.info("Entered fetchParamsForProcessing() method in "+ CLASS_NAME +".");
	}
	
	/**
	 * This method is used to fetch Mission, Engine and Diff MTO based on Frame MTO.
	 * @param enterACCApplicationsSuppMTOSummaryDVO
	 */
	public void fetchMissionEngineDiffBasedOnFrame(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO) {
		log.info("Entered fetchMissionEngineDiffBasedOnFrame() method in "+ CLASS_NAME +".");
		
			
			ArrayList<EnterACCSuppFEMDMTODTO> m_lEnterACCSuppFEMDMTODTO = new ArrayList<EnterACCSuppFEMDMTODTO>();
			ArrayList<EnterACCSuppFEMDMTODTO> m_lAvoidDuplicateFrameComboEnterACCSuppFEMDMTODTO = new ArrayList<EnterACCSuppFEMDMTODTO>();
			EnterACCSuppFEMDMTODTO avoidDuplicateEnterACCSuppFEMDMTODTO = null;
			EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO;
			
			int currentItr=0;
			//Based on BASE/CURRENT FRAME fetch the BASE/CURRENT - Engine, Mission and Differential.
    		for(EnterACCApplicationsDVO baseFrameApplicationsDVO : enterACCApplicationsSuppMTOSummaryDVO.getM_lBaseApplications()){
    			if(!checkForDuplicateData(baseFrameApplicationsDVO, 
    					enterACCApplicationsSuppMTOSummaryDVO.getM_lCurrentApplications().get(currentItr),
    					m_lAvoidDuplicateFrameComboEnterACCSuppFEMDMTODTO)){
    				enterACCSuppFEMDMTODTO = accProcessingBatchDAO.fetchMissionEngineDiffBasedOnFrame(baseFrameApplicationsDVO, 
        					enterACCApplicationsSuppMTOSummaryDVO.getM_lCurrentApplications().get(currentItr), 
        					enterACCApplicationsSuppMTOSummaryDVO);
    				//Previously - Add only if Base & Current Frame are present in the Database.
    				//After Change - Add even if either Base or Current Frame is present
        			if(enterACCSuppFEMDMTODTO.isPresentInDB()){
        				m_lEnterACCSuppFEMDMTODTO.add(enterACCSuppFEMDMTODTO);
        			}
        			avoidDuplicateEnterACCSuppFEMDMTODTO = new EnterACCSuppFEMDMTODTO();
        			avoidDuplicateEnterACCSuppFEMDMTODTO.setBaseFrameApplication(baseFrameApplicationsDVO);
        			avoidDuplicateEnterACCSuppFEMDMTODTO.setCurrentFrameApplication(enterACCApplicationsSuppMTOSummaryDVO.getM_lCurrentApplications().get(currentItr));
        			m_lAvoidDuplicateFrameComboEnterACCSuppFEMDMTODTO.add(avoidDuplicateEnterACCSuppFEMDMTODTO);
    			}
    			currentItr++;
    		}
    		
    		enterACCApplicationsSuppMTOSummaryDVO.setM_lEnterACCSuppFEMDMTODTOList(m_lEnterACCSuppFEMDMTODTO);
		
    	log.info("\n Exiting method - fetchMissionEngineDiffBasedOnFrame() in "+CLASS_NAME);
	}
	
	private boolean checkForDuplicateData(EnterACCApplicationsDVO baseFrameApplicationsDVO, EnterACCApplicationsDVO currentFrameApplicationsDVO,
			ArrayList<EnterACCSuppFEMDMTODTO> m_lEnterACCSuppFEMDMTODTO){
		boolean isDuplicate=false;
		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO : m_lEnterACCSuppFEMDMTODTO){
			if(enterACCSuppFEMDMTODTO.getBaseFrameApplication().equals(baseFrameApplicationsDVO) &&
					enterACCSuppFEMDMTODTO.getCurrentFrameApplication().equals(currentFrameApplicationsDVO)){
				isDuplicate = true;
				break;
			}
		}
		return isDuplicate;
	}
	
	/**
	 * This method loads the data Part Level Details tab on Supplier Summary MTO screen which is being processed 
	 * and kept to be used directly on screen.
	 * @param enterACCApplicationsSuppMTOSummaryDVO
	 * @throws Exception 
	 */
	public void loadPartLevelDetailsTabData(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO) throws Exception {
		log.info("\n Entering method - loadPartLevelDetailsTabData() in "+CLASS_NAME);
		
		Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> m_hmpEnterACCPreviousEventPartDetailsDTO;
		Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> m_hmpEnterACCCurrentEventPartDetailsDTO;
		//Main Part Data List which is used to display data on the screen which will be feed in with MTO ACC Data in the method.
		ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList = new ArrayList<EnterACCSuppSummaryPartLevelDataDTO>();
		//Main Data collector HashMap which consist the data of each MTO ACC Data for a specific part record as displayed on the screen.
		Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO = 
			new HashMap<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>>();
		Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO = 
			new HashMap<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>>();
		
		//Fetch the data for the previous Event.
		m_hmpEnterACCPreviousEventPartDetailsDTO = accProcessingBatchDAO.fetchPreviousEventData(enterACCApplicationsSuppMTOSummaryDVO);
		
		//Fetch the data for the current Event.
		m_hmpEnterACCCurrentEventPartDetailsDTO = accProcessingBatchDAO.fetchCurrentEventData(enterACCApplicationsSuppMTOSummaryDVO);
		
		//Compare the two events data and form the final list(Part Level data list). Here need to check Variance at part level and also consider the whether balanced or un-balanced or both data is to be fetched based on the criteria selected on the previous Enter ACC screen.
		/*if(null!=m_hmpEnterACCPreviousEventPartDetailsDTO&&!m_hmpEnterACCPreviousEventPartDetailsDTO.isEmpty()&&
				null!=m_hmpEnterACCCurrentEventPartDetailsDTO&&!m_hmpEnterACCCurrentEventPartDetailsDTO.isEmpty()){*/
			/*enterACCApplicationsSuppMTOSummaryDVO.setM_lEnterACCSuppSummaryPartLevelDataDTOList( 
					compareDataBetweenCurrentAndPreviousEvent(enterACCApplicationsSuppMTOSummaryDVO,
							m_hmpEnterACCPreviousEventPartDetailsDTO, m_hmpEnterACCCurrentEventPartDetailsDTO));*/
		//}
		//Method call to process complete ACC data with Rules
		compareDataBetweenCurrentAndPreviousEvent(enterACCApplicationsSuppMTOSummaryDVO,
				m_hmpEnterACCPreviousEventPartDetailsDTO, m_hmpEnterACCCurrentEventPartDetailsDTO, m_lEnterACCSuppSummaryPartLevelDataDTOList,
				m_hmpACCDisplayLabelEffDateDTO, m_hmpEnterACCSuppSummaryACCDataDTO);
		
		//Save the data processed by the above method(compareDataBetweenCurrentAndPreviousEvent) call in staging table (FCACC2)
		saveProcessedACCDataInStagingTable(enterACCApplicationsSuppMTOSummaryDVO, m_lEnterACCSuppSummaryPartLevelDataDTOList, 
				m_hmpACCDisplayLabelEffDateDTO, m_hmpEnterACCSuppSummaryACCDataDTO);
		
		log.info("\n Exiting method - loadPartLevelDetailsTabData() in "+CLASS_NAME);
	}
	
	/**
	 * This event is used to compare the list of Part Data between previous and Current event.
	 * @param m_lEnterACCSuppFEMDMTODTOList
	 * @param m_lEnterACCPreviousEventPartDetailsDTO
	 * @param m_lEnterACCCurrentEventPartDetailsDTO
	 * @return
	 * @throws Exception 
	 */
	private void compareDataBetweenCurrentAndPreviousEvent(
			EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> m_hmpEnterACCPreviousEventPartDetailsDTO,
			Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> m_hmpEnterACCCurrentEventPartDetailsDTO, 
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList, 
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO, 
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO ) throws Exception {
		log.info("\n Entering method - compareDataBwtweenCurrentAndPreviousEvent() in "+CLASS_NAME);
		/*
		Main Part Data List which is used to display data on the screen which will be feed in with MTO ACC Data in the method.
			m_lEnterACCSuppSummaryPartLevelDataDTOList
		Main Data collector HashMap which consist the data of each MTO ACC Data for a specific part record as displayed on the screen.
			m_hmpEnterACCSuppSummaryACCDataDTO m_hmpACCDisplayLabelEffDateDTO
		*/
			
			for(EnterACCSuppFEMDMTODTO femdDTO : enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
				
				//Compare data between current and previous event.
				//All the methods below shall have data returned in  2 objects namely - m_lEnterACCSuppSummaryDataDTOList and m_hmpEnterACCSuppSummaryACCDataDTO.
				
				if(null!=m_hmpEnterACCPreviousEventPartDetailsDTO && null!=m_hmpEnterACCCurrentEventPartDetailsDTO ){
					
					//Data should be present for both current and present part details
					if(null!=m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO) && null!=m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO)){
						//This method compares and processes data for exact match and all single indicator changes. Also applies rules. 
						compareCurrentAndPreviousEvent(enterACCApplicationsSuppMTOSummaryDVO, femdDTO,
								m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO), 
								m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO), m_lEnterACCSuppSummaryPartLevelDataDTOList, 
								m_hmpEnterACCSuppSummaryACCDataDTO, m_hmpACCDisplayLabelEffDateDTO);
					}
					
					//Check for proc sect change as proc changed to/From may not be selected by the user. And if no proc sect is selected then skip this method
					//Also Check if both the Base and Current MTO are present 
					//Not necessary data should be present for both current and present part details
					if(!(enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim().isEmpty() 
							&& enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim().isEmpty())
							&&(null!=femdDTO.getBaseFrameApplication() && null!=femdDTO.getBaseFrameApplication().getTargetModel()
								&& !femdDTO.getBaseFrameApplication().getTargetModel().isEmpty() &&
								null!=femdDTO.getCurrentFrameApplication() && null!=femdDTO.getCurrentFrameApplication().getTargetModel()
								&& !femdDTO.getCurrentFrameApplication().getTargetModel().isEmpty() )
							){
						//This method compares and processes data for proc group change and 
						//also if there are other indicator changes then they are too handled within this method and based and rules are applied.
						compareCurrentAndPreviousEventForProcChange(enterACCApplicationsSuppMTOSummaryDVO, femdDTO,
								m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO), 
								m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO), m_lEnterACCSuppSummaryPartLevelDataDTOList, 
								m_hmpEnterACCSuppSummaryACCDataDTO, m_hmpACCDisplayLabelEffDateDTO);
					}
					
					//Data should be present for both current and present part details
					if(null!=m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO) && null!=m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO)){
						//This method compares and processes data for Multiple indicator change which is to be handled based on hierarchy 
						compareCurrentAndPreviousEventForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO, femdDTO,
								m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO), 
								m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO), m_lEnterACCSuppSummaryPartLevelDataDTOList, 
								m_hmpEnterACCSuppSummaryACCDataDTO, m_hmpACCDisplayLabelEffDateDTO);
					}
					//INC0726363  / CPT-357 - commenting out partial part match and show part dropped or added instead
					//Check the remaining m_hmpEnterACCPreviousEventPartDetailsDTO and m_hmpEnterACCCurrentEventPartDetailsDTO parts if they have ACC 
					//then pick data and add in the main list(m_lEnterACCSuppSummaryPartLevelDataDTOList)
					//Data should be present for both current and present part details
					/*if(null!=m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO) && null!=m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO)){
						//This method compares and processes data if there are any unmatched data and there is a possibility of partial part match 
						//and also looks if rules can be applied
						compareCurrentAndPreviousEventForRemainingUnMatched(enterACCApplicationsSuppMTOSummaryDVO, femdDTO,
								m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO), 
								m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO), m_lEnterACCSuppSummaryPartLevelDataDTOList, 
								m_hmpEnterACCSuppSummaryACCDataDTO, m_hmpACCDisplayLabelEffDateDTO);
					}*/
					//INC0726363  / CPT-357 - end
					//Once step 1 is completed mark parts as Added or dropped which are not processed after this there shall be no unprocessed part left in the list.
					compareCurrentAndPreviousEventForAddedDroppedParts(enterACCApplicationsSuppMTOSummaryDVO, femdDTO,
							m_hmpEnterACCPreviousEventPartDetailsDTO.get(femdDTO), 
							m_hmpEnterACCCurrentEventPartDetailsDTO.get(femdDTO), m_lEnterACCSuppSummaryPartLevelDataDTOList, 
							m_hmpEnterACCSuppSummaryACCDataDTO, m_hmpACCDisplayLabelEffDateDTO);

				}
			}
		
		log.info("\n Exiting method - compareDataBwtweenCurrentAndPreviousEvent() in "+CLASS_NAME);
	}
	
	/**
	 * This method compare the previous and current event part data, calculates the ACCs and arranges the in the form to be shown on the screen. 
	 * @param enterACCApplicationsSuppMTOSummaryDVO
	 * @param femdDTO
	 * @param m_lEnterACCPreviousEventPartDetailsDTO
	 * @param m_lEnterACCCurrentEventPartDetailsDTO
	 * @param m_lEnterACCSuppSummaryPartLevelDataDTOList
	 * @param m_hmpEnterACCSuppSummaryACCDataDTO
	 * @param m_hmpACCDisplayLabelEffDateDTO
	 * @throws Exception 
	 * @throws ApplicationException
	 */
	private void compareCurrentAndPreviousEvent(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			EnterACCSuppFEMDMTODTO femdDTO, //EnterACCApplicationsDVO baseApplicationsDVO, EnterACCApplicationsDVO currentApplicationsDVO, 
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO,
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCCurrentEventPartDetailsDTO,
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO) throws Exception {
		log.info("\n Entering method - compareCurrentAndPreviousEvent() in "+CLASS_NAME);
			boolean matchFound = false;
			EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO ;//= new EnterACCSuppSummaryDataDTO();
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
			EnterACCSuppSummaryACCCostDataDTO enterACCSuppSummaryACCCostDataDTO;
			EnterACCSuppSummaryACCDataDTO enterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lenterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList=null;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lEnterACCSuppSummaryACCDataDTO;
			BigDecimal m_decTotalACC = new BigDecimal(0.0000);
			BigDecimal m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
			
			for(EnterACCEventPartDetailsDTO currentEventPartDetails : m_lEnterACCCurrentEventPartDetailsDTO){
				matchFound = false;
				   
				for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
					
					if(!previousEventPartDetails.isM_bolMatchDone()){
						
						if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "EXACT_MATCH")){
							//Match Done hence mark the previous events record as done irrespective of the further validation
							previousEventPartDetails.setM_bolMatchDone(true);
							currentEventPartDetails.setM_bolMatchDone(true);
							m_decTotalACC = new BigDecimal(0.0000);
							m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
							//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
							//Commented the if condition as ACC is to be fetched even when unresolved is selected
							//if(!StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES)){
							//get the ACC from the data base
							m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
									currentEventPartDetails, previousEventPartDetails, "EXACT_MATCH", "");								
							//}
							
							if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
								//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
								//and even if Variance exists add one more ACC data and mark ACC data as pending
								//If variance is not present after ACC is applied consider record as resolved balance
								
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										currentEventPartDetails.getM_strProcSectCode(),
										currentEventPartDetails.getM_strSupplierNumber(),
										currentEventPartDetails.getM_strSupplierName(),
										currentEventPartDetails.getM_strPlantLocCode(),
										currentEventPartDetails.getM_strPartSectionCode(),
										currentEventPartDetails.getM_strModelCatCode(),
										currentEventPartDetails.getM_decShareRatePercent(),
										currentEventPartDetails.getM_intPartQty(),
										currentEventPartDetails.getM_strPartColorCode(),
										currentEventPartDetails.getM_strPartNumber(),
										currentEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.EXACT_MATCH.value),
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME
										);
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
									
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
										List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
												,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "", "" );
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										for(Map<String,Object> accData : allACCs){
											//ACC Cost Data - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(String)accData.get("RULE_ID")!=null ? (String)accData.get("RULE_ID") : "",
													((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
													Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
													Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
													String.valueOf((Integer)accData.get("ACC_STATUS")),
													"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
													(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										}
										
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										
										for(Map<String,Object> accData : allACCs){
											//ACC Cost Data - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															(String)accData.get("ACC_COMMENTS"), 
															(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
															(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
											    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
													new EnterACCSuppSummaryACCCommentsDTO(
															(String)accData.get("ACC_COMMENTS"), 
															(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																	(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
													    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
													String.valueOf((Integer)accData.get("ACC_STATUS")),
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null && !(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("B")
															|| ((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("C")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "S",
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("B")
																|| ((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("C"))
																? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_CB_TO_S : ""
													);
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										}
										
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										
										//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										
									}
									
									
									//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
									if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
									}
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											rawACCData.getM_decACCAmount(),
											rawACCData.getM_decACCAmount(),
											rawACCData.getM_strAppCostChangeCode(),
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													rawACCData.getM_strAccComments(), 
													rawACCData.getM_strAccCommentDesc(), 
													rawACCData.getM_strAccCommentNote()),
											rawACCData.getM_strAccStatus(),
											rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
											rawACCData.getM_strAccRulePartCharMatch(),
											rawACCData.getM_strEffectiveDate(),
											rawACCData.getM_strModifiedBy(),
											rawACCData.getM_strModifiedDate(),
											rawACCData.getM_strBaseOrCurrentEvent());
									
									m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
									if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
										m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
									}
									enterACCSuppSummaryACCCostDataDTOList.set(
											fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
											enterACCSuppSummaryACCCostDataDTO);
								}
								
								//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
								if(!(m_decTotalACC.compareTo(findVariance(
										previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
										previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent())) == 0)){
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									
									//TODO - Assign ACC by Rule NOT required for Exact match
									//String[] strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
									String[] strRuleACC = null;
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											(findVariance(
													previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
													previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											(findVariance(
													previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
													previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
											strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,
											"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME);
									
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : AccRuleEnum.FSTN.getRuleDescText())//TODO Changed Assign ACC by Rule
											);
										
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME,
														""));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME));
											}
										}
										
									}
									//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
									if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									} else {
										enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
									}
								}
								
								BigDecimal balanceCost = ((((findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
										currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
										.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())))
												.subtract(findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
								//TODO needs to be removed as from batch need to process data irrespective of DataToDisplay
								//Display data on screen based on the what user has selected in the DataToDisplay field. 
								/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
										&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
												&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
										|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
													previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
											(findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
													.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
															previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())),
											findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											enterACCSuppSummaryACCCostDataDTOList,
											balanceCost,
											femdDTO
											);
									
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								/*} else {
									//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
									if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
										m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
									}
								}*/
							} else {
								//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
								//Check if variance exist
								//Display data on screen based on the what user has selected in the DataToDisplay field.
								if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
											previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent()))
										.compareTo(BigDecimal.ZERO) == 0)){
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.EXACT_MATCH.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									//TODO - Assign ACC by Rule NOT required for Exact match
									//String[] strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
									String[] strRuleACC = null;
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												"",
												strRuleACC==null ?  "" : AccRuleEnum.FSTN.getRuleDescText() //TODO Changed Assign ACC by Rule
											);
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME,
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME,"");
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									}
									
									//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : AccRuleEnum.FSTN.getRuleDescText() //TODO Changed Assign ACC by Rule
													));
										
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME,""
													));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME));
											}
										}
										
									}
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
													previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
													previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
											m_strDefaultEffectiveDate,"",""
											, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME);
									
									//List of ACC Data
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_SAME);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount()),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
													currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
													, currentEventPartDetails.getM_decMCCAmount()),
											(findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
													.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
															previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())),
											findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											enterACCSuppSummaryACCCostDataDTOList,
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_decEndCostAmount(),
													previousEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_decMCCAmount(), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											femdDTO
											);
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								}
							}
							matchFound = true;
						}
					}
				}
			//CPT-449 start
			}
			
			for(EnterACCEventPartDetailsDTO currentEventPartDetails : m_lEnterACCCurrentEventPartDetailsDTO){
				if(!currentEventPartDetails.isM_bolMatchDone()){
				matchFound = false;
			//CPT-449 end
				if(!matchFound){
					for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
						
						if(!previousEventPartDetails.isM_bolMatchDone()){
							
							if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "SUPP_CHANGE_MATCH")){
								//Match Done hence mark the previous events record as done irrespective of the further validation
								previousEventPartDetails.setM_bolMatchDone(true);
								currentEventPartDetails.setM_bolMatchDone(true);
								//TODO - Assign ACC by Rule
								String[] strRuleACC = null;
								//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, null);
									if(strRuleACC==null)
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails, null);
									if(strRuleACC==null)
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails, null);
								//}
								//***************Previous Code Block START***********************
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "SUPP_CHANGE_MATCH", "BASE");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SUPPLIER_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);
									
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "SUPP_CHANGE_MATCH", "BASE" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
															!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															"",
															strRuleACC==null ? "" : strRuleACC[3]));
											
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														"")
											);
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									
									BigDecimal balanceCost = ((findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
												enterACCSuppSummaryACCCostDataDTOList,
												/*(((findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
														.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
																previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())))
																.subtract(findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																		currentEventPartDetails.getM_decShareRatePercent()))).subtract(m_decTotalACC)*/
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
											previousEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												previousEventPartDetails.getM_strProcSectCode(),
												previousEventPartDetails.getM_strSupplierNumber(),
												previousEventPartDetails.getM_strSupplierName(),
												previousEventPartDetails.getM_strPlantLocCode(),
												previousEventPartDetails.getM_strPartSectionCode(),
												previousEventPartDetails.getM_strModelCatCode(),
												previousEventPartDetails.getM_decShareRatePercent(),
												previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_strPartColorCode(),
												previousEventPartDetails.getM_strPartNumber(),
												previousEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SUPPLIER_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
												);
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
																strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																m_strDefaultEffectiveDate,
																m_strDefaultEffectiveDate,
																strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																		: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																"",
																strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
															));
											
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
												m_strDefaultEffectiveDate,"",""
												, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent())*/
												new BigDecimal(0.0000),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								
								
								
								
								//***************Previous Code Block END**************************
								
								
								
								//***************Current Code Block START**************************
								
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "SUPP_CHANGE_MATCH", "CURRENT_SAME");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SUPPLIER_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);
									
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "SUPP_CHANGE_MATCH", "CURRENT_SAME" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																String.valueOf((Integer)accData.get("ACC_STATUS")) ,
																(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
																(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
																!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
																(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
																&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																		? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															"",
															strRuleACC==null ? "" : strRuleACC[3]));
											
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															""));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												currentEventPartDetails.getM_strProcSectCode(),
												currentEventPartDetails.getM_strSupplierNumber(),
												currentEventPartDetails.getM_strSupplierName(),
												currentEventPartDetails.getM_strPlantLocCode(),
												currentEventPartDetails.getM_strPartSectionCode(),
												currentEventPartDetails.getM_strModelCatCode(),
												currentEventPartDetails.getM_decShareRatePercent(),
												currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_strPartColorCode(),
												currentEventPartDetails.getM_strPartNumber(),
												currentEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SUPPLIER_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
												);
										
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
																strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																m_strDefaultEffectiveDate,
																m_strDefaultEffectiveDate,
																strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																		: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																"",
																strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
															));
											
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
												m_strDefaultEffectiveDate,"","",
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								//***************Current Code Block END**************************
								matchFound = true;
							}
						}
					}
				}
				
				if(!matchFound){
					for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
						
						if(!previousEventPartDetails.isM_bolMatchDone()){
							
							if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "PROC_GROUP_CHANGE_MATCH")){
								//Match Done hence mark the previous events record as done irrespective of the further validation
								previousEventPartDetails.setM_bolMatchDone(true);
								currentEventPartDetails.setM_bolMatchDone(true);
								//TODO - Assign ACC by Rule NOT required for PROC group change
								//String[] strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
								String[] strRuleACC = null;
								//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
									strRuleACC = new String[]{"A16","","",""};

								//}
								
								//***************Previous Code Block START***********************
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "PROC_GROUP_CHANGE_MATCH", "BASE");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
											+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode(),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "PROC_GROUP_CHANGE_MATCH", "BASE" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															"",
															strRuleACC==null ? "" : strRuleACC[3]));
											
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															""));
										
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
												enterACCSuppSummaryACCCostDataDTOList,
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
											previousEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												previousEventPartDetails.getM_strProcSectCode(),
												previousEventPartDetails.getM_strSupplierNumber(),
												previousEventPartDetails.getM_strSupplierName(),
												previousEventPartDetails.getM_strPlantLocCode(),
												previousEventPartDetails.getM_strPartSectionCode(),
												previousEventPartDetails.getM_strModelCatCode(),
												previousEventPartDetails.getM_decShareRatePercent(),
												previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_strPartColorCode(),
												previousEventPartDetails.getM_strPartNumber(),
												previousEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
												+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode(),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
												);
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													//"A16",//TODO Changed Assign ACC by Rule,
													//"A16",//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],
													strRuleACC==null ?  "" : strRuleACC[0],
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"PROCCHG", 
															"CHANGE IN PROC SECTION", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"PROCCHG", 
															"CHANGE IN PROC SECTION", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															//"A16",//TODO Changed Assign ACC by Rule
															//"A16",//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],
															strRuleACC==null ?  "" : strRuleACC[0],
															false,
															new EnterACCSuppSummaryACCCommentsDTO(
																	"PROCCHG", 
																	"CHANGE IN PROC SECTION", 
																	""),
															new EnterACCSuppSummaryACCCommentsDTO(
																	"PROCCHG", 
																	"CHANGE IN PROC SECTION", 
																	""),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(
																			"PROCCHG", 
																			"CHANGE IN PROC SECTION", 
																			""),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
													//"A16",//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"PROCCHG", 
															"CHANGE IN PROC SECTION", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
													m_strDefaultEffectiveDate,"",""
													, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent())*/
												new BigDecimal(0.0000),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								
								
								
								
								//***************Previous Code Block END**************************
								
								
								
								//***************Current Code Block START**************************
								
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "PROC_GROUP_CHANGE_MATCH", "CURRENT_SAME");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
											+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode(),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "PROC_GROUP_CHANGE_MATCH", "CURRENT_SAME" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH")),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														"",
														strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														""));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												currentEventPartDetails.getM_strProcSectCode(),
												currentEventPartDetails.getM_strSupplierNumber(),
												currentEventPartDetails.getM_strSupplierName(),
												currentEventPartDetails.getM_strPlantLocCode(),
												currentEventPartDetails.getM_strPartSectionCode(),
												currentEventPartDetails.getM_strModelCatCode(),
												currentEventPartDetails.getM_decShareRatePercent(),
												currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_strPartColorCode(),
												currentEventPartDetails.getM_strPartNumber(),
												currentEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
												+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode(),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
												);
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														//"A16",
														//"A16",
														strRuleACC==null ?  "" : strRuleACC[0],
														strRuleACC==null ?  "" : strRuleACC[0],
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"PROCCHG", 
															"CHANGE IN PROC SECTION", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"PROCCHG", 
															"CHANGE IN PROC SECTION", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															//"A16", //TODO Changed Assign ACC by Rule
															//"A16",//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],
															strRuleACC==null ?  "" : strRuleACC[0],
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															//"A16",//TODO Changed Assign ACC by Rule
															//"A16",//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],
															strRuleACC==null ?  "" : strRuleACC[0],		
															false,
															new EnterACCSuppSummaryACCCommentsDTO(
																	"PROCCHG", 
																	"CHANGE IN PROC SECTION", 
																	""),
															new EnterACCSuppSummaryACCCommentsDTO(
																	"PROCCHG", 
																	"CHANGE IN PROC SECTION", 
																	""),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(
																			"PROCCHG", 
																			"CHANGE IN PROC SECTION", 
																			""),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												//"A16",//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[0],
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														"PROCCHG", 
														"CHANGE IN PROC SECTION", 
														""),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
												m_strDefaultEffectiveDate,"","",
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								//***************Current Code Block END**************************
								matchFound = true;
							}
						}
					}
				}
				
				if(!matchFound){
					for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
						
						if(!previousEventPartDetails.isM_bolMatchDone()){
							
							if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "SHARE_RATE_CHANGE_MATCH")){
								//Match Done hence mark the previous events record as done irrespective of the further validation
								previousEventPartDetails.setM_bolMatchDone(true);
								currentEventPartDetails.setM_bolMatchDone(true);
								
								//TODO - Assign ACC by Rule
								String[] strRuleACC = null;
								//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
								//}
									
								//***************Previous Code Block START***********************
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "SHARE_RATE_CHANGE_MATCH", "BASE");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SHARE_RATE_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "SHARE_RATE_CHANGE_MATCH", "BASE" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																String.valueOf((Integer)accData.get("ACC_STATUS")),
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													""));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
												enterACCSuppSummaryACCCostDataDTOList,
												/*(((findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
														.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
																previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())))
																.subtract(findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																		currentEventPartDetails.getM_decShareRatePercent()))).subtract(m_decTotalACC)*/
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&& */!((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
											previousEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												previousEventPartDetails.getM_strProcSectCode(),
												previousEventPartDetails.getM_strSupplierNumber(),
												previousEventPartDetails.getM_strSupplierName(),
												previousEventPartDetails.getM_strPlantLocCode(),
												previousEventPartDetails.getM_strPartSectionCode(),
												previousEventPartDetails.getM_strModelCatCode(),
												previousEventPartDetails.getM_decShareRatePercent(),
												previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_strPartColorCode(),
												previousEventPartDetails.getM_strPartNumber(),
												previousEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SHARE_RATE_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
												);
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
													m_strDefaultEffectiveDate,"",""
													, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent())*/
												new BigDecimal(0.0000),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								
								
								
								
								//***************Previous Code Block END**************************
								
								
								
								//***************Current Code Block START**************************
								
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "SHARE_RATE_CHANGE_MATCH", "CURRENT_SAME");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SHARE_RATE_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "SHARE_RATE_CHANGE_MATCH", "CURRENT_SAME" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														"",
														strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														""));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												currentEventPartDetails.getM_strProcSectCode(),
												currentEventPartDetails.getM_strSupplierNumber(),
												currentEventPartDetails.getM_strSupplierName(),
												currentEventPartDetails.getM_strPlantLocCode(),
												currentEventPartDetails.getM_strPartSectionCode(),
												currentEventPartDetails.getM_strModelCatCode(),
												currentEventPartDetails.getM_decShareRatePercent(),
												currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_strPartColorCode(),
												currentEventPartDetails.getM_strPartNumber(),
												currentEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.SHARE_RATE_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
												);
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
												m_strDefaultEffectiveDate,"","",
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								//***************Current Code Block END**************************
								matchFound = true;
							}
						}
					}
				}
				
				if(!matchFound){
					for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
						
						if(!previousEventPartDetails.isM_bolMatchDone()){
							
							if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "PART_QTY_CHANGE_MATCH")){
								//Match Done hence mark the previous events record as done irrespective of the further validation
								previousEventPartDetails.setM_bolMatchDone(true);
								currentEventPartDetails.setM_bolMatchDone(true);
								//TODO - Assign ACC by Rule
								String[] strRuleACC = null;
								//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
								//}
								//***************Previous Code Block START***********************
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								log.info("part QTY change prt number - "+previousEventPartDetails.getM_strPartNumber()+" "+currentEventPartDetails.getM_strPartNumber());
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "PART_QTY_CHANGE_MATCH", "BASE");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									
									//Main Part Details Data Object
									log.info("Appr ACC found for PTY Quantity single indicator base");
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_QTY_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "PART_QTY_CHANGE_MATCH", "BASE" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													""));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
												enterACCSuppSummaryACCCostDataDTOList,
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
											previousEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										log.info("No Appr ACC found for single indicator qty change base");
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												previousEventPartDetails.getM_strProcSectCode(),
												previousEventPartDetails.getM_strSupplierNumber(),
												previousEventPartDetails.getM_strSupplierName(),
												previousEventPartDetails.getM_strPlantLocCode(),
												previousEventPartDetails.getM_strPartSectionCode(),
												previousEventPartDetails.getM_strModelCatCode(),
												previousEventPartDetails.getM_decShareRatePercent(),
												previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_strPartColorCode(),
												previousEventPartDetails.getM_strPartNumber(),
												previousEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_QTY_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
												);
										
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
												m_strDefaultEffectiveDate,"",""
												, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount()),
												new BigDecimal(0.0000),
												new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount())),
												/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent())*/
												new BigDecimal(0.0000),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								
								
								
								
								//***************Previous Code Block END**************************
								
								
								
								//***************Current Code Block START**************************
								
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "PART_QTY_CHANGE_MATCH", "CURRENT_SAME");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance
									log.info("APPR ACC found for PART_QTY_CHANGE_MATCH single indicator current same");
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_QTY_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);
									
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "PART_QTY_CHANGE_MATCH", "CURRENT_SAME" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
									
									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())) == 0)){
										
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
													.subtract(m_decTotalACC),
												strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														"",
														strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														""));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
											|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
										){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												balanceCost,
												femdDTO
												);
										
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/
									
								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent()))
											.compareTo(BigDecimal.ZERO) == 0)){
										log.info("No approved ACC found for PART_QTY_CHANGE_MATCH single indicator current same");
										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												currentEventPartDetails.getM_strProcSectCode(),
												currentEventPartDetails.getM_strSupplierNumber(),
												currentEventPartDetails.getM_strSupplierName(),
												currentEventPartDetails.getM_strPlantLocCode(),
												currentEventPartDetails.getM_strPartSectionCode(),
												currentEventPartDetails.getM_strModelCatCode(),
												currentEventPartDetails.getM_decShareRatePercent(),
												currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_strPartColorCode(),
												currentEventPartDetails.getM_strPartNumber(),
												currentEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_QTY_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
												);
										
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"", 
															"", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
											
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}
										
										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
										
										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															new EnterACCSuppSummaryACCCommentsDTO(),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
														));
											
											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}
											
										}
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
												m_strDefaultEffectiveDate,"","",
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										
										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
												findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												enterACCSuppSummaryACCCostDataDTOList,
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
												femdDTO
												);
										
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								//***************Current Code Block END**************************
								matchFound = true;
							}
						}
					}
				}
				
				if(!matchFound){
					for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){

						if(!previousEventPartDetails.isM_bolMatchDone()){

							if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "DESIGN_SECT_CHANGE_MATCH")){
								//Match Done hence mark the previous events record as done irrespective of the further validation
								previousEventPartDetails.setM_bolMatchDone(true);
								currentEventPartDetails.setM_bolMatchDone(true);
								//TODO - Assign ACC by Rule NOT required for Design Sect change
								//String[] strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
								String[] strRuleACC = null;
								//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
									strRuleACC = new String[]{"A16","","",""};
								//}
								
								//***************Previous Code Block START***********************
								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "DESIGN_SECT_CHANGE_MATCH", "BASE");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance

									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.DESIGN_SECT_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
									);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){

										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "DESIGN_SECT_CHANGE_MATCH", "BASE" );

											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
																Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																String.valueOf((Integer)accData.get("ACC_STATUS")),
																"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
																(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}

											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();

											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																				((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																				new EnterACCSuppSummaryACCCommentsDTO(
																						(String)accData.get("ACC_COMMENTS"), 
																						(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																								(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																										((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																										String.valueOf((Integer)accData.get("ACC_STATUS")) ,
																										(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
																												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
																												!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
																														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
																														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																														? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}

											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);

											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}

										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}

										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);

										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
														rawACCData.getM_strAccStatus(),
														rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
																rawACCData.getM_strAccRulePartCharMatch(),
																rawACCData.getM_strEffectiveDate(),
																rawACCData.getM_strModifiedBy(),
																rawACCData.getM_strModifiedDate(),
																rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}

									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())) == 0)){

										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.

										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()))
														.subtract(m_decTotalACC),
														(findVariance(
																previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
																previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
																previousEventPartDetails.getM_decShareRatePercent()))
																.subtract(m_decTotalACC),
																strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																		: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
																strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);

										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													""));

											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}

										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
											previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
													|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
														new BigDecimal(0.0000),
														new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
																previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
																, previousEventPartDetails.getM_decMCCAmount())),
																new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
													enterACCSuppSummaryACCCostDataDTOList,
													balanceCost,
													femdDTO
										);

										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/

								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
													previousEventPartDetails.getM_decShareRatePercent()))
													.compareTo(BigDecimal.ZERO) == 0)){

										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												previousEventPartDetails.getM_strProcSectCode(),
												previousEventPartDetails.getM_strSupplierNumber(),
												previousEventPartDetails.getM_strSupplierName(),
												previousEventPartDetails.getM_strPlantLocCode(),
												previousEventPartDetails.getM_strPartSectionCode(),
												previousEventPartDetails.getM_strModelCatCode(),
												previousEventPartDetails.getM_decShareRatePercent(),
												previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_strPartColorCode(),
												previousEventPartDetails.getM_strPartNumber(),
												previousEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.DESIGN_SECT_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
										);

										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());

										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){

											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													);

											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													//"A16",//TODO Changed Assign ACC by Rule,
													//"A16",//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],
													strRuleACC==null ?  "" : strRuleACC[0],
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"DESSECCHANGE", 
															"DES SEC CHANGE FROM ONE TO ANOTHER", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"DESSECCHANGE", 
															"DES SEC CHANGE FROM ONE TO ANOTHER", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");

											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}

										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															//"A16",//TODO Changed Assign ACC by Rule
															//"A16",//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],
															strRuleACC==null ?  "" : strRuleACC[0],
															false,
															new EnterACCSuppSummaryACCCommentsDTO(
																	"DESSECCHANGE", 
																	"DES SEC CHANGE FROM ONE TO ANOTHER", 
																	""),
															new EnterACCSuppSummaryACCCommentsDTO(
																	"DESSECCHANGE", 
																	"DES SEC CHANGE FROM ONE TO ANOTHER", 
																	""),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
														));

											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(
																			"DESSECCHANGE", 
																			"DES SEC CHANGE FROM ONE TO ANOTHER", 
																			""),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
												}
											}

										}

										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
														previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
														previousEventPartDetails.getM_decShareRatePercent()),
														findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
																previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
																previousEventPartDetails.getM_decShareRatePercent()),
														//"A16",//TODO Changed Assign ACC by Rule,
														strRuleACC==null ?  "" : strRuleACC[0],
														false,
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																"DESSECCHANGE", 
																"DES SEC CHANGE FROM ONE TO ANOTHER", 
																""),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
														m_strDefaultEffectiveDate,"",""
														, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);

										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);

										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
														previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
														, previousEventPartDetails.getM_decMCCAmount()),
														new BigDecimal(0.0000),
														new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
																previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
																, previousEventPartDetails.getM_decMCCAmount())),
																/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent())*/
																new BigDecimal(0.0000),
																enterACCSuppSummaryACCCostDataDTOList,
																findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
																		previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
																		previousEventPartDetails.getM_decShareRatePercent()),
																		femdDTO
										);

										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}




								//***************Previous Code Block END**************************



								//***************Current Code Block START**************************

								//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
								//get the ACC from the data base
								m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
										currentEventPartDetails, previousEventPartDetails, "DESIGN_SECT_CHANGE_MATCH", "CURRENT");								
								m_decTotalACC = new BigDecimal(0.0000);
								m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
								if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
									//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
									//and even if Variance exists add one more ACC data and mark ACC data as pending
									//If variance is not present after ACC is applied consider record as resolved balance

									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.DESIGN_SECT_CHANGE.value),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
									);

									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){

										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "DESIGN_SECT_CHANGE_MATCH", "CURRENT_SAME" );

											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
																Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																String.valueOf((Integer)accData.get("ACC_STATUS")),
																"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
																(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}

											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();

											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																				((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																				new EnterACCSuppSummaryACCCommentsDTO(
																						(String)accData.get("ACC_COMMENTS"), 
																						(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																								(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																										((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																										String.valueOf((Integer)accData.get("ACC_STATUS")) ,
																										(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
																												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
																												!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
																														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
																														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																														? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}

											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);

											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}

										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);

										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
														rawACCData.getM_strAccStatus(),
														rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
																rawACCData.getM_strAccRulePartCharMatch(),
																rawACCData.getM_strEffectiveDate(),
																rawACCData.getM_strModifiedBy(),
																rawACCData.getM_strModifiedDate(),
																rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}

									//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
									if(!(m_decTotalACC.compareTo(findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())) == 0)){

										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.

										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(findVariance(
														new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()))
														.subtract(m_decTotalACC),
														(findVariance(
																new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
																new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																currentEventPartDetails.getM_decShareRatePercent()))
																.subtract(m_decTotalACC),
														//"A16",//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],
														false,
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);

										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														"",
														strRuleACC==null ? "" : strRuleACC[3]));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														//"A16",//TODO Changed Assign ACC by Rule
														//"A16",//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],
														strRuleACC==null ?  "" : strRuleACC[0],
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														""));

											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}

										}
										//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
										if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
										} else {
											enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
										}
									}
									BigDecimal balanceCost = ((findVariance(
											new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
											new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
											currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
									//Display data on screen based on the what user has selected in the DataToDisplay field. 
									/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
													|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
														findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
																currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
																findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
																		currentEventPartDetails.getM_decShareRatePercent()),
																		enterACCSuppSummaryACCCostDataDTOList,
																		balanceCost,
																		femdDTO
										);

										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/

								} else {
									//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
									//Check if variance exist
									if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&& */!((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
													.compareTo(BigDecimal.ZERO) == 0)){

										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												currentEventPartDetails.getM_strProcSectCode(),
												currentEventPartDetails.getM_strSupplierNumber(),
												currentEventPartDetails.getM_strSupplierName(),
												currentEventPartDetails.getM_strPlantLocCode(),
												currentEventPartDetails.getM_strPartSectionCode(),
												currentEventPartDetails.getM_strModelCatCode(),
												currentEventPartDetails.getM_decShareRatePercent(),
												currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_strPartColorCode(),
												currentEventPartDetails.getM_strPartNumber(),
												currentEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.DESIGN_SECT_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
										);

										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());

										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){

											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													"",
													strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);

											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													//"A16",//TODO Changed Assign ACC by Rule,
													//"A16",//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],
													strRuleACC==null ?  "" : strRuleACC[0],
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															"DESSECCHANGE", 
															"DES SEC CHANGE FROM ONE TO ANOTHER", 
															""),
													new EnterACCSuppSummaryACCCommentsDTO(
															"DESSECCHANGE", 
															"DES SEC CHANGE FROM ONE TO ANOTHER", 
															""),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");

											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
											);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										}

										//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
										int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

										if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
											m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															//"A16",//TODO Changed Assign ACC by Rule
															//"A16",//TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[0],
															strRuleACC==null ?  "" : strRuleACC[0],
															false,
															new EnterACCSuppSummaryACCCommentsDTO(
																	"DESSECCHANGE", 
																	"DES SEC CHANGE FROM ONE TO ANOTHER", 
																	""),
															new EnterACCSuppSummaryACCCommentsDTO(
																	"DESSECCHANGE", 
																	"DES SEC CHANGE FROM ONE TO ANOTHER", 
																	""),
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
															BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
														));

											//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
											if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
													&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
												for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
													m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
													.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
															new EnterACCSuppSummaryACCCostDataDTO(
																	new BigDecimal(0.0000),
																	new BigDecimal(0.0000),
																	"",
																	false,
																	false,
																	new EnterACCSuppSummaryACCCommentsDTO(
																			"DESSECCHANGE", 
																			"DES SEC CHANGE FROM ONE TO ANOTHER", 
																			""),
																	BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																	"",
																	"",
																	m_strDefaultEffectiveDate,
																	"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
												}
											}

										}

										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
														new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
														currentEventPartDetails.getM_decShareRatePercent()),
														findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
																new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																currentEventPartDetails.getM_decShareRatePercent()),
														//"A16",//TODO Changed Assign ACC by Rule,
														strRuleACC==null ?  "" : strRuleACC[0],
														false,
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																"DESSECCHANGE", 
																"DES SEC CHANGE FROM ONE TO ANOTHER", 
																""),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
														m_strDefaultEffectiveDate,"","",
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);

										//List of ACC Data
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);

										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
														currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
														, currentEventPartDetails.getM_decMCCAmount()),
														findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
																currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
																, currentEventPartDetails.getM_decMCCAmount()),
																findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																		currentEventPartDetails.getM_decShareRatePercent()),
																		enterACCSuppSummaryACCCostDataDTOList,
																		findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
																				new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																				currentEventPartDetails.getM_decShareRatePercent()),
																				femdDTO
										);

										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
									}
								}
								//***************Current Code Block END**************************
								matchFound = true;
							}
						}
					}
				}
				
				//Below block is for part color code change only between current and base event
				if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
					if(!matchFound){

						for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){

							if(!previousEventPartDetails.isM_bolMatchDone()){

								if(compareCurrentAndPreviousPartData(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, previousEventPartDetails, "PART_COLOR_CODE_CHANGE_MATCH")){
									//Match Done hence mark the previous events record as done irrespective of the further validation
									previousEventPartDetails.setM_bolMatchDone(true);
									currentEventPartDetails.setM_bolMatchDone(true);
									//TODO - Assign ACC by Rule NOT required for Design Sect change
									//String[] strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
									String[] strRuleACC = null;
									if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.PCCC, previousEventPartDetails, currentEventPartDetails, null);
									}

									//***************Previous Code Block START***********************
									//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
									//get the ACC from the data base
									m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
											currentEventPartDetails, previousEventPartDetails, "PART_COLOR_CODE_CHANGE_MATCH", "BASE");								
									m_decTotalACC = new BigDecimal(0.0000);
									m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
									if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
										//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
										//and even if Variance exists add one more ACC data and mark ACC data as pending
										//If variance is not present after ACC is applied consider record as resolved balance

										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												previousEventPartDetails.getM_strProcSectCode(),
												previousEventPartDetails.getM_strSupplierNumber(),
												previousEventPartDetails.getM_strSupplierName(),
												previousEventPartDetails.getM_strPlantLocCode(),
												previousEventPartDetails.getM_strPartSectionCode(),
												previousEventPartDetails.getM_strModelCatCode(),
												previousEventPartDetails.getM_decShareRatePercent(),
												previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_strPartColorCode(),
												previousEventPartDetails.getM_strPartNumber(),
												previousEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_COLOR_CODE_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
										);
										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){

											//Check the acc seq and arrange the ACC fetched accordingly.
											if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
												//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
												List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
														,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "PART_COLOR_CODE_CHANGE_MATCH", "BASE" );

												//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
												//which includes the ACC drop down 
												//and left of this we display Effective date and rule id so creating one more object for the same.
												m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

												//List of ACCs seq - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
												for(Map<String,Object> accData : allACCs){
													//ACC Cost Data - Effective Date and Rule ID.
													enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
															(String)accData.get("RULE_ID"),
															((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
																	Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																	Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																	String.valueOf((Integer)accData.get("ACC_STATUS")),
																	"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
																	(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
													enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												}

												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
														"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												//List of ACCs seq - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();

												for(Map<String,Object> accData : allACCs){
													//ACC Cost Data - ACC, Comments and Status
													enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
															(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
															false,
															new EnterACCSuppSummaryACCCommentsDTO(
																	(String)accData.get("ACC_COMMENTS"), 
																	(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																			(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																					new EnterACCSuppSummaryACCCommentsDTO(
																							(String)accData.get("ACC_COMMENTS"), 
																							(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																									(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																											((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																											String.valueOf((Integer)accData.get("ACC_STATUS")) ,
																											(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
																													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
																													!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
																															(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
																															&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
													enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												}

												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
														"Previous",
														"Current",
														"Difference",
														"MCC",
														"Balance",
														enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);

												//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											}

											//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
											if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
												EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
												for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
													accCostData = new EnterACCSuppSummaryACCCostDataDTO();
													accCostData.setM_decACCCost(new BigDecimal(0.0000));
													accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
													accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
													accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
													enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
												}
											}

											//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
											//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
											//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);

											//ACC Cost Data
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													rawACCData.getM_decACCAmount(),
													rawACCData.getM_decACCAmount(),
													rawACCData.getM_strAppCostChangeCode(),
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															rawACCData.getM_strAccComments(), 
															rawACCData.getM_strAccCommentDesc(), 
															rawACCData.getM_strAccCommentNote()),
															rawACCData.getM_strAccStatus(),
															rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
																	rawACCData.getM_strAccRulePartCharMatch(),
																	rawACCData.getM_strEffectiveDate(),
																	rawACCData.getM_strModifiedBy(),
																	rawACCData.getM_strModifiedDate(),
																	rawACCData.getM_strBaseOrCurrentEvent());
											m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
											if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
												m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
											}
											enterACCSuppSummaryACCCostDataDTOList.set(
													fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
													enterACCSuppSummaryACCCostDataDTO);
										}

										//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
										if(!(m_decTotalACC.compareTo(findVariance(
												previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent())) == 0)){

											//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
											//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.

											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(findVariance(
															previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
															previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
															previousEventPartDetails.getM_decShareRatePercent()))
															.subtract(m_decTotalACC),
															(findVariance(
																	previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
																	previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
																	previousEventPartDetails.getM_decShareRatePercent()))
																	.subtract(m_decTotalACC),
																	strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																			false,
																			false,
																			new EnterACCSuppSummaryACCCommentsDTO(),
																			strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																					: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																					strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
																							strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																									m_strDefaultEffectiveDate,
																									"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);

											int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

											if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
																strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																		m_strDefaultEffectiveDate,
																		m_strDefaultEffectiveDate,
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
																				"",
																				strRuleACC==null ? "" : strRuleACC[3]));

												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(),
																		new EnterACCSuppSummaryACCCommentsDTO(),
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
																				BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
																				BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														""));

												//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
												if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
														&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
													for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
														m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
														.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
																new EnterACCSuppSummaryACCCostDataDTO(
																		new BigDecimal(0.0000),
																		new BigDecimal(0.0000),
																		"",
																		false,
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(),
																		BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																		"",
																		"",
																		m_strDefaultEffectiveDate,
																		"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
													}
												}

											}
											//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
											if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
												enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
											} else {
												enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
											}
										}
										BigDecimal balanceCost = ((findVariance(
												previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
										//Display data on screen based on the what user has selected in the DataToDisplay field. 
										/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
													|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
														new BigDecimal(0.0000),
														new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
																previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
																, previousEventPartDetails.getM_decMCCAmount())),
																new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
													enterACCSuppSummaryACCCostDataDTOList,
													balanceCost,
													femdDTO
										);

										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
										/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/

									} else {
										//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
										//Check if variance exist
										if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
													previousEventPartDetails.getM_decShareRatePercent()))
													.compareTo(BigDecimal.ZERO) == 0)){

											//Main Part Details Data Object
											enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
													previousEventPartDetails.getM_strProcSectCode(),
													previousEventPartDetails.getM_strSupplierNumber(),
													previousEventPartDetails.getM_strSupplierName(),
													previousEventPartDetails.getM_strPlantLocCode(),
													previousEventPartDetails.getM_strPartSectionCode(),
													previousEventPartDetails.getM_strModelCatCode(),
													previousEventPartDetails.getM_decShareRatePercent(),
													previousEventPartDetails.getM_intPartQty(),
													previousEventPartDetails.getM_strPartColorCode(),
													previousEventPartDetails.getM_strPartNumber(),
													previousEventPartDetails.getM_strPartName(),
													BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_COLOR_CODE_CHANGE.value),
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);

											enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
											enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());

											//Check the acc seq and arrange the ACC fetched accordingly.
											if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){

												//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
												//which includes the ACC drop down 
												//and left of this we display Effective date and rule id so creating one more object for the same.
												m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

												//List of ACCs seq - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
																strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																		m_strDefaultEffectiveDate,
																		m_strDefaultEffectiveDate,
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																				"",
																				strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);

												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
														"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												//List of ACCs seq - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														//TODO Changed Assign ACC by Rule,
														//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],
																strRuleACC==null ?  "" : strRuleACC[0],
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(
																				"COLOR CHANGE", 
																				"VARIANCE DUE TO COLOR", 
																		""),
																		new EnterACCSuppSummaryACCCommentsDTO(
																				"COLOR CHANGE", 
																				"VARIANCE DUE TO COLOR", 
																		""),
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																				BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
																				BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");

												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
														"Previous",
														"Current",
														"Difference",
														"MCC",
														"Balance",
														enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											}

											//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
											int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

											if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
																		strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																				m_strDefaultEffectiveDate,
																				m_strDefaultEffectiveDate,
																				strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																						: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																						"",
																						strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));

												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[0],
																		strRuleACC==null ?  "" : strRuleACC[0],
																				false,
																				new EnterACCSuppSummaryACCCommentsDTO(
																						"COLOR CHANGE", 
																						"VARIANCE DUE TO COLOR", 
																				""),
																				new EnterACCSuppSummaryACCCommentsDTO(
																						"COLOR CHANGE", 
																						"VARIANCE DUE TO COLOR", 
																				""),
																				strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																						: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																						BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
																						BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
														));

												//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
												if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
														&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
													for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
														m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
														.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
																new EnterACCSuppSummaryACCCostDataDTO(
																		new BigDecimal(0.0000),
																		new BigDecimal(0.0000),
																		"",
																		false,
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(
																				"COLOR CHANGE", 
																				"VARIANCE DUE TO COLOR", 
																		""),
																		BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																		"",
																		"",
																		m_strDefaultEffectiveDate,
																		"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
													}
												}

											}

											//ACC Cost Data
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
															previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
															previousEventPartDetails.getM_decShareRatePercent()),
															findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
																	previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
																	previousEventPartDetails.getM_decShareRatePercent()),
																	//TODO Changed Assign ACC by Rule,
																	strRuleACC==null ?  "" : strRuleACC[0],
																			false,
																			false,
																			new EnterACCSuppSummaryACCCommentsDTO(
																					"COLOR CHANGE", 
																					"VARIANCE DUE TO COLOR", 
																			""),
																			strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																					: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																					strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
																							strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
																									m_strDefaultEffectiveDate,"",""
																									, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);

											//List of ACC Data
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);

											//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
											//Adding the ACC Cost in the object
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
													findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
															previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
															, previousEventPartDetails.getM_decMCCAmount()),
															new BigDecimal(0.0000),
															new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
																	previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
																	, previousEventPartDetails.getM_decMCCAmount())),
																	/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent())*/
																	new BigDecimal(0.0000),
																	enterACCSuppSummaryACCCostDataDTOList,
																	findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
																			previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
																			previousEventPartDetails.getM_decShareRatePercent()),
																			femdDTO
											);

											//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
											if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
												m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
											}
											if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
												//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
											} else {
												//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
												m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
												m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
												m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
											}
										}
									}




									//***************Previous Code Block END**************************



									//***************Current Code Block START**************************

									//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
									//get the ACC from the data base
									m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCData(enterACCApplicationsSuppMTOSummaryDVO, 
											currentEventPartDetails, previousEventPartDetails, "PART_COLOR_CODE_CHANGE_MATCH", "CURRENT");								
									m_decTotalACC = new BigDecimal(0.0000);
									m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
									if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
										//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
										//and even if Variance exists add one more ACC data and mark ACC data as pending
										//If variance is not present after ACC is applied consider record as resolved balance

										//Main Part Details Data Object
										enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
												currentEventPartDetails.getM_strProcSectCode(),
												currentEventPartDetails.getM_strSupplierNumber(),
												currentEventPartDetails.getM_strSupplierName(),
												currentEventPartDetails.getM_strPlantLocCode(),
												currentEventPartDetails.getM_strPartSectionCode(),
												currentEventPartDetails.getM_strModelCatCode(),
												currentEventPartDetails.getM_decShareRatePercent(),
												currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_strPartColorCode(),
												currentEventPartDetails.getM_strPartNumber(),
												currentEventPartDetails.getM_strPartName(),
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_COLOR_CODE_CHANGE.value),
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
										);

										enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
										enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
										//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
										if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
											m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
										}
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){

											//Check the acc seq and arrange the ACC fetched accordingly.
											if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
												//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
												List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
														,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "PART_COLOR_CODE_CHANGE_MATCH", "CURRENT_SAME" );

												//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
												//which includes the ACC drop down 
												//and left of this we display Effective date and rule id so creating one more object for the same.
												m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

												//List of ACCs seq - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
												for(Map<String,Object> accData : allACCs){
													//ACC Cost Data - Effective Date and Rule ID.
													enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
															(String)accData.get("RULE_ID"),
															((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
																	Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																	Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
																	String.valueOf((Integer)accData.get("ACC_STATUS")),
																	"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
																	(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
													enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												}

												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
														"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												//List of ACCs seq - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();

												for(Map<String,Object> accData : allACCs){
													//ACC Cost Data - ACC, Comments and Status
													enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
															(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
															false,
															new EnterACCSuppSummaryACCCommentsDTO(
																	(String)accData.get("ACC_COMMENTS"), 
																	(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																			(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																					new EnterACCSuppSummaryACCCommentsDTO(
																							(String)accData.get("ACC_COMMENTS"), 
																							(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																									(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
																											((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
																											String.valueOf((Integer)accData.get("ACC_STATUS")) ,
																											(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
																													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
																													!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
																															(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
																															&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																															? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
													enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												}

												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
														"Previous",
														"Current",
														"Difference",
														"MCC",
														"Balance",
														enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);

												//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											}

											//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
											if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
												EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
												for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
													accCostData = new EnterACCSuppSummaryACCCostDataDTO();
													accCostData.setM_decACCCost(new BigDecimal(0.0000));
													accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
													accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
													accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
													enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
												}
											}
											//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
											//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
											//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);

											//ACC Cost Data
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													rawACCData.getM_decACCAmount(),
													rawACCData.getM_decACCAmount(),
													rawACCData.getM_strAppCostChangeCode(),
													false,
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															rawACCData.getM_strAccComments(), 
															rawACCData.getM_strAccCommentDesc(), 
															rawACCData.getM_strAccCommentNote()),
															rawACCData.getM_strAccStatus(),
															rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
																	rawACCData.getM_strAccRulePartCharMatch(),
																	rawACCData.getM_strEffectiveDate(),
																	rawACCData.getM_strModifiedBy(),
																	rawACCData.getM_strModifiedDate(),
																	rawACCData.getM_strBaseOrCurrentEvent());
											m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
											if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
												m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
											}
											enterACCSuppSummaryACCCostDataDTOList.set(
													fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
													enterACCSuppSummaryACCCostDataDTO);
										}

										//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
										if(!(m_decTotalACC.compareTo(findVariance(
												new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent())) == 0)){

											//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
											//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.

											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(findVariance(
															new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
															new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
															currentEventPartDetails.getM_decShareRatePercent()))
															.subtract(m_decTotalACC),
															(findVariance(
																	new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
																	new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																	currentEventPartDetails.getM_decShareRatePercent()))
																	.subtract(m_decTotalACC),
																	//TODO Changed Assign ACC by Rule
																	strRuleACC==null ?  "" : strRuleACC[0],
																			false,
																			false,
																			new EnterACCSuppSummaryACCCommentsDTO(),
																			strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																					: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																					strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
																							strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																									m_strDefaultEffectiveDate,
																									"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);

											int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

											if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
																strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																		m_strDefaultEffectiveDate,
																		m_strDefaultEffectiveDate,
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
																				"",
																				strRuleACC==null ? "" : strRuleACC[3]));

												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[0],
																		strRuleACC==null ?  "" : strRuleACC[0],
																				false,
																				new EnterACCSuppSummaryACCCommentsDTO(),
																				new EnterACCSuppSummaryACCCommentsDTO(),
																				strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																						: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
																						BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
																						BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														""));

												//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
												if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
														&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
													for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
														m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
														.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
																new EnterACCSuppSummaryACCCostDataDTO(
																		new BigDecimal(0.0000),
																		new BigDecimal(0.0000),
																		"",
																		false,
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(),
																		BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																		"",
																		"",
																		m_strDefaultEffectiveDate,
																		"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
													}
												}

											}
											//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
											if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
												enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
											} else {
												enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
											}
										}
										BigDecimal balanceCost = ((findVariance(
												new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
										//Display data on screen based on the what user has selected in the DataToDisplay field. 
										/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
													&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
													|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
										//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
										//Adding the ACC Cost in the object
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
												new BigDecimal(0.0000),
												findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
														currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
														findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
																currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
																findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
																		currentEventPartDetails.getM_decShareRatePercent()),
																		enterACCSuppSummaryACCCostDataDTOList,
																		balanceCost,
																		femdDTO
										);

										if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
										} else {
											//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
											m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
										}
										/*} else {
										//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
										if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
											m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
										}
									}*/

									} else {
										//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
										//Check if variance exist
										if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
											&& */!((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
													.compareTo(BigDecimal.ZERO) == 0)){

											//Main Part Details Data Object
											enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
													currentEventPartDetails.getM_strProcSectCode(),
													currentEventPartDetails.getM_strSupplierNumber(),
													currentEventPartDetails.getM_strSupplierName(),
													currentEventPartDetails.getM_strPlantLocCode(),
													currentEventPartDetails.getM_strPartSectionCode(),
													currentEventPartDetails.getM_strModelCatCode(),
													currentEventPartDetails.getM_decShareRatePercent(),
													currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_strPartColorCode(),
													currentEventPartDetails.getM_strPartNumber(),
													currentEventPartDetails.getM_strPartName(),
													BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_COLOR_CODE_CHANGE.value),
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);

											enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
											enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());

											//Check the acc seq and arrange the ACC fetched accordingly.
											if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){

												//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
												//which includes the ACC drop down 
												//and left of this we display Effective date and rule id so creating one more object for the same.
												m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();

												//List of ACCs seq - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
																strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																		m_strDefaultEffectiveDate,
																		m_strDefaultEffectiveDate,
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																				"",
																				strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);

												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
														"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												//List of ACCs seq - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[0],
																strRuleACC==null ?  "" : strRuleACC[0],
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(
																				"COLOR CHANGE", 
																				"VARIANCE DUE TO COLOR",  
																		""),
																		new EnterACCSuppSummaryACCCommentsDTO(
																				"COLOR CHANGE", 
																				"VARIANCE DUE TO COLOR", 
																		""),
																		strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																				: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																				BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
																				BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");

												enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
												enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
														"Previous",
														"Current",
														"Difference",
														"MCC",
														"Balance",
														enterACCSuppSummaryACCCostDataDTOList
												);
												m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);

												m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											}

											//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
											int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));

											if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
																		strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
																				m_strDefaultEffectiveDate,
																				m_strDefaultEffectiveDate,
																				strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																						: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																						"",
																						strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));

												m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																strRuleACC==null ?  "" : strRuleACC[0],
																		strRuleACC==null ?  "" : strRuleACC[0],
																				false,
																				new EnterACCSuppSummaryACCCommentsDTO(
																						"COLOR CHANGE", 
																						"VARIANCE DUE TO COLOR",  
																				""),
																				new EnterACCSuppSummaryACCCommentsDTO(
																						"COLOR CHANGE", 
																						"VARIANCE DUE TO COLOR", 
																				""),
																				strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																						: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																						BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
																						BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
														));

												//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
												if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
														&& m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
													for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
														m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
														.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
																new EnterACCSuppSummaryACCCostDataDTO(
																		new BigDecimal(0.0000),
																		new BigDecimal(0.0000),
																		"",
																		false,
																		false,
																		new EnterACCSuppSummaryACCCommentsDTO(
																				"COLOR CHANGE", 
																				"VARIANCE DUE TO COLOR", 
																		""),
																		BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																		"",
																		"",
																		m_strDefaultEffectiveDate,
																		"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
													}
												}

											}

											//ACC Cost Data
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
															new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
															currentEventPartDetails.getM_decShareRatePercent()),
															findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
																	new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																	currentEventPartDetails.getM_decShareRatePercent()),
																	strRuleACC==null ?  "" : strRuleACC[0],
																			false,
																			false,
																			new EnterACCSuppSummaryACCCommentsDTO(
																					"COLOR CHANGE", 
																					"VARIANCE DUE TO COLOR", 
																			""),
																			strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																					: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																					strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
																							strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
																									m_strDefaultEffectiveDate,"","",
																									BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);

											//List of ACC Data
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
											enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);

											//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
											//Adding the ACC Cost in the object
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
													new BigDecimal(0.0000),
													findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
															currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
															, currentEventPartDetails.getM_decMCCAmount()),
															findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
																	currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
																	, currentEventPartDetails.getM_decMCCAmount()),
																	findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																			currentEventPartDetails.getM_decShareRatePercent()),
																			enterACCSuppSummaryACCCostDataDTOList,
																			findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
																					new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																					currentEventPartDetails.getM_decShareRatePercent()),
																					femdDTO
											);

											//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
											if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
												m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
											}
											if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
												//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
											} else {
												//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
												m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
												m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
												m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
											}
										}
									}
									//***************Current Code Block END**************************
									matchFound = true;
								}
							}
						}

					}
				}
			
				
				//Clearing off the variable.
				m_lenterACCSuppSummaryACCDataDetailsDTOList = new ArrayList<EnterACCSuppSummaryACCDataDetailsDTO>();
			}	//CPT-449
			}
		
		log.info("\n Exiting method - compareCurrentAndPreviousEvent() in "+CLASS_NAME);
	}
	
	/**
	 * This method looks for ACCs which are assinged by batch but for uncommon part data.
	 * @param enterACCApplicationsSuppMTOSummaryDVO
	 * @param femdDTO
	 * @param m_lEnterACCPreviousEventPartDetailsDTO
	 * @param m_lEnterACCCurrentEventPartDetailsDTO
	 * @param m_lEnterACCSuppSummaryPartLevelDataDTOList
	 * @param m_hmpEnterACCSuppSummaryACCDataDTO
	 * @param m_hmpACCDisplayLabelEffDateDTO
	 * @throws Exception 
	 * @throws ApplicationException
	 */
	private void compareCurrentAndPreviousEventForRemainingUnMatched(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			EnterACCSuppFEMDMTODTO femdDTO,  
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO,
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCCurrentEventPartDetailsDTO,
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO) throws Exception {
		log.info("\n Entering method - compareCurrentAndPreviousEventForRemainingUnMatched() in "+CLASS_NAME);
			EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO;
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
			EnterACCSuppSummaryACCCostDataDTO enterACCSuppSummaryACCCostDataDTO;
			EnterACCSuppSummaryACCDataDTO enterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lenterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList=null;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lEnterACCSuppSummaryACCDataDTO;
			BigDecimal m_decTotalACC = new BigDecimal(0.0000);
			BigDecimal m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
			EnterACCEventPartDetailsDTO previousEventPartDetails = new EnterACCEventPartDetailsDTO();
			//boolean matchFound = false;
			for(EnterACCEventPartDetailsDTO currentEventPartDetails : m_lEnterACCCurrentEventPartDetailsDTO){
				//matchFound = false;			
				if(!currentEventPartDetails.isM_bolMatchDone()){
					/*for(EnterACCEventPartDetailsDTO previousEventPartDetailsObj : m_lEnterACCPreviousEventPartDetailsDTO){
						if(!previousEventPartDetailsObj.isM_bolMatchDone()){
							matchFound = true;
							previousEventPartDetails = previousEventPartDetailsObj;
						}
					}*/
					
					String[] strRuleACC = assignACCBasedOnRules(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, m_lEnterACCPreviousEventPartDetailsDTO);
					if(strRuleACC==null)
						strRuleACC = assignACCBasedOnRules(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails, m_lEnterACCPreviousEventPartDetailsDTO);
					if(strRuleACC==null)
						strRuleACC = assignACCBasedOnRules(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails, m_lEnterACCPreviousEventPartDetailsDTO);
					
					if(null!=strRuleACC){
						
						//Based on hirarchy decide on the indicator to display (below if is written in the hierarchy)
						String changeIndicatorToShowBasedOnHierarchy = null;
						if(!previousEventPartDetails.getM_strProcSectCode().trim().equalsIgnoreCase(currentEventPartDetails.getM_strProcSectCode().trim()))
							changeIndicatorToShowBasedOnHierarchy = 
								BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value())
								+(" "+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode());
						else if(null==changeIndicatorToShowBasedOnHierarchy &&
								!previousEventPartDetails.getM_strSupplierNumber().trim().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber().trim()))
							changeIndicatorToShowBasedOnHierarchy = 
								BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(ACC_PART_INDICATOR.SUPPLIER_CHANGE.value());
						else if(null==changeIndicatorToShowBasedOnHierarchy &&
								!(previousEventPartDetails.getM_intPartQty()==currentEventPartDetails.getM_intPartQty()))
							changeIndicatorToShowBasedOnHierarchy = 
								BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(ACC_PART_INDICATOR.QTY_CHANGE.value());
						else if(null==changeIndicatorToShowBasedOnHierarchy &&
								!(previousEventPartDetails.getM_decShareRatePercent().compareTo(currentEventPartDetails.getM_decShareRatePercent())==0))
							changeIndicatorToShowBasedOnHierarchy = 
								BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value());
						else if(null==changeIndicatorToShowBasedOnHierarchy &&
								!previousEventPartDetails.getM_strPartSectionCode().trim().equalsIgnoreCase(currentEventPartDetails.getM_strPartSectionCode().trim()))
							changeIndicatorToShowBasedOnHierarchy = 
								BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value());
							
							
							//Check if ACC is present for this current and previous.
							//get the ACC from the data base
							m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForUnMatched(enterACCApplicationsSuppMTOSummaryDVO, 
									currentEventPartDetails, previousEventPartDetails, "B"); //CPT-357
							
							m_decTotalACC = new BigDecimal(0.0000);
							m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
							//previousEventPartDetails.setM_bolMatchDone(true);
							currentEventPartDetails.setM_bolMatchDone(true);
							if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
								//If exists then create 2 objects for the part details list.
								
								//***************Previous Code Block START***********************
								//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
								//and even if Variance exists add one more ACC data and mark ACC data as pending
								//If variance is not present after ACC is applied consider record as resolved balance
								
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										previousEventPartDetails.getM_strProcSectCode(),
										previousEventPartDetails.getM_strSupplierNumber(),
										previousEventPartDetails.getM_strSupplierName(),
										previousEventPartDetails.getM_strPlantLocCode(),
										previousEventPartDetails.getM_strPartSectionCode(),
										previousEventPartDetails.getM_strModelCatCode(),
										previousEventPartDetails.getM_decShareRatePercent(),
										previousEventPartDetails.getM_intPartQty(),
										previousEventPartDetails.getM_strPartColorCode(),
										previousEventPartDetails.getM_strPartNumber(),
										previousEventPartDetails.getM_strPartName(),
										"",//Adding it later below in the for loop getting value from -rawACCData.getM_strPartDistinguishingReason().
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
										);
								
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
									
									if(StringUtils.equals(rawACCData.getM_strBaseOrCurrentEvent(), "B")){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											
											enterACCSuppSummaryPartLevelDataDTO.setM_strPartACCIndicator(changeIndicatorToShowBasedOnHierarchy);
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											
											//ACC Report regeneration failure PRB0011972
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOSRemainingUnMatched(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "", "BASE" );
											
											
											//List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
													//,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "", "BASE" );
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "","");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
								}
								
								//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
								if(!(m_decTotalACC.compareTo(findVariance(
										previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
										previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
										previousEventPartDetails.getM_decShareRatePercent())) == 0)){
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											(findVariance(
													previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											(findVariance(
													previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value 
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//TODO Changed Assign ACC by Rule,
											strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
											strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,
											"","",
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
									
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															"",
															strRuleACC==null ? "" : strRuleACC[3]));
										
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														""));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
											}
										}
										
									}
									//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
									if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									} else {
										enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
									}
								}
								BigDecimal balanceCost = ((findVariance(
										previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
										previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
										previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
								//Display data on screen based on the what user has selected in the DataToDisplay field. 
								/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
										&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
												&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
										|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
													previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
											new BigDecimal(0.0000),
											new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount())),
											new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
											enterACCSuppSummaryACCCostDataDTOList,
											/*(((findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
													.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
															previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())))
															.subtract(findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																	currentEventPartDetails.getM_decShareRatePercent()))).subtract(m_decTotalACC)*/
											balanceCost,
											femdDTO
											);
									
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								/*} else {
									//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
									if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
										m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
									}
								}*/
							}
							//TODO - Need an else block as present in other compare methods
							else {
								//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
								//Check if variance exist
								if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									&& */!((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
										previousEventPartDetails.getM_decShareRatePercent()))
										.compareTo(BigDecimal.ZERO) == 0)){
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											changeIndicatorToShowBasedOnHierarchy,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												"",
												strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									}
									
									//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
															strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
															"",
															strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
														));
										
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
											}
										}
										
									}
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,"",""
											, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
									
									//List of ACC Data
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount()),
											new BigDecimal(0.0000),
											new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount())),
											/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent())*/
											new BigDecimal(0.0000),
											enterACCSuppSummaryACCCostDataDTOList,
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											femdDTO
											);
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								}
							}
							//***************Previous Code Block END***********************
							
							//***************Current Code Block Start***********************
							m_decTotalACC = new BigDecimal(0.0000);
							m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
							//CPT-357 start
							m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForUnMatched(enterACCApplicationsSuppMTOSummaryDVO, 
									currentEventPartDetails, previousEventPartDetails, "C");
							//CPT-357 end
							if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
								//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
								//and even if Variance exists add one more ACC data and mark ACC data as pending
								//If variance is not present after ACC is applied consider record as resolved balance
								
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										currentEventPartDetails.getM_strProcSectCode(),
										currentEventPartDetails.getM_strSupplierNumber(),
										currentEventPartDetails.getM_strSupplierName(),
										currentEventPartDetails.getM_strPlantLocCode(),
										currentEventPartDetails.getM_strPartSectionCode(),
										currentEventPartDetails.getM_strModelCatCode(),
										currentEventPartDetails.getM_decShareRatePercent(),
										currentEventPartDetails.getM_intPartQty(),
										currentEventPartDetails.getM_strPartColorCode(),
										currentEventPartDetails.getM_strPartNumber(),
										currentEventPartDetails.getM_strPartName(),
										"",//Adding it later below in the for loop getting value from -rawACCData.getM_strPartDistinguishingReason().
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
										);
								
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								
								for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
									
									if(StringUtils.equals(rawACCData.getM_strBaseOrCurrentEvent(), "C")){
										
										//Check the acc seq and arrange the ACC fetched accordingly.
										if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
											enterACCSuppSummaryPartLevelDataDTO.setM_strPartACCIndicator(changeIndicatorToShowBasedOnHierarchy);
											//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
											
											//ACC Report regeneration failure PRB0011972
											List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOSRemainingUnMatched(enterACCApplicationsSuppMTOSummaryDVO
													,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "", "CURRENT_SAME" );
											
											//List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
												//	,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, "", "CURRENT_SAME" );
											
											//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
											//which includes the ACC drop down 
											//and left of this we display Effective date and rule id so creating one more object for the same.
											m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
											
											//List of ACCs seq - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - Effective Date and Rule ID.
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("RULE_ID"),
														((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
														String.valueOf((Integer)accData.get("ACC_STATUS")),
														"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
														(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
													"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											//List of ACCs seq - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
											
											for(Map<String,Object> accData : allACCs){
												//ACC Cost Data - ACC, Comments and Status
												enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
														(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														new EnterACCSuppSummaryACCCommentsDTO(
																(String)accData.get("ACC_COMMENTS"), 
																(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																		(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
														    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "","");
												enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
											}
											
											enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
													"Previous",
													"Current",
													"Difference",
													"MCC",
													"Balance",
													enterACCSuppSummaryACCCostDataDTOList
													);
											m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
											
											m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
											
											//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
											enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										}
										
										//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
										if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
											EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
											for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
												accCostData = new EnterACCSuppSummaryACCCostDataDTO();
												accCostData.setM_decACCCost(new BigDecimal(0.0000));
												accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
												accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
												accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
												enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
											}
										}
										//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
										//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
										//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
										
										//ACC Cost Data
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_decACCAmount(),
												rawACCData.getM_strAppCostChangeCode(),
												false,
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														rawACCData.getM_strAccComments(), 
														rawACCData.getM_strAccCommentDesc(), 
														rawACCData.getM_strAccCommentNote()),
												rawACCData.getM_strAccStatus(),
												rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
												rawACCData.getM_strAccRulePartCharMatch(),
												rawACCData.getM_strEffectiveDate(),
												rawACCData.getM_strModifiedBy(),
												rawACCData.getM_strModifiedDate(),
												rawACCData.getM_strBaseOrCurrentEvent());
										m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
										if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
											m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
										}
										enterACCSuppSummaryACCCostDataDTOList.set(
												fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
												enterACCSuppSummaryACCCostDataDTO);
									}
								}
								
								//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
								if(!(m_decTotalACC.compareTo(findVariance(
										new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
										new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent())) == 0)){
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											(findVariance(
													new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											(findVariance(
													new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value 
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//TODO Changed Assign ACC by Rule,
											strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
											strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,
											"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
									
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
															strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
															m_strDefaultEffectiveDate,
															m_strDefaultEffectiveDate,
															strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																	: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
															"",
															strRuleACC==null ? "" : strRuleACC[3]));
										
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																new EnterACCSuppSummaryACCCommentsDTO(),
																strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																		: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
																BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
																BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
																""));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
											}
										}
										
									}
									//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
									if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									} else {
										enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
									}
								}
								BigDecimal balanceCost = ((findVariance(
										new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
										new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
								//Display data on screen based on the what user has selected in the DataToDisplay field. 
								/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
										&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
												&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
										|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											new BigDecimal(0.0000),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
											findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent()),
											enterACCSuppSummaryACCCostDataDTOList,
											balanceCost,
											femdDTO
											);
									
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								/*} else {
									//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
									if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
										m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
									}
								}*/
								
							} else {
								//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
								//Check if variance exist
								if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
										new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent()))
										.compareTo(BigDecimal.ZERO) == 0)){
									
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											changeIndicatorToShowBasedOnHierarchy,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);
									
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												"",
												strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									}
									
									//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													));
										
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																new EnterACCSuppSummaryACCCommentsDTO(),
																strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																		: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
																BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
																BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
											}
										}
										
									}
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,"","",
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
									
									//List of ACC Data
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											new BigDecimal(0.0000),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
													currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
													, currentEventPartDetails.getM_decMCCAmount()),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
													currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
													, currentEventPartDetails.getM_decMCCAmount()),
											findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											enterACCSuppSummaryACCCostDataDTOList,
											findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											femdDTO
											);
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								}
							}
							
							//***************Current Code Block END***********************

						//}
						
						previousEventPartDetails = new EnterACCEventPartDetailsDTO();
					}
				}
			}
			
		log.info("\n Exiting method - compareCurrentAndPreviousEventForRemainingUnMatched() in "+CLASS_NAME);
	}
	
	/**
	 * This method process data for multiple change indicator(such as Proc group, supplier no, design sect etc) and apply the indicator based on hierarchy.
	 * @param enterACCApplicationsSuppMTOSummaryDVO
	 * @param femdDTO
	 * @param m_lEnterACCPreviousEventPartDetailsDTO
	 * @param m_lEnterACCCurrentEventPartDetailsDTO
	 * @param m_lEnterACCSuppSummaryPartLevelDataDTOList
	 * @param m_hmpEnterACCSuppSummaryACCDataDTO
	 * @param m_hmpACCDisplayLabelEffDateDTO
	 * @throws Exception 
	 * @throws ApplicationException
	 */
	private void compareCurrentAndPreviousEventForMultipleIndicatorChange(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			EnterACCSuppFEMDMTODTO femdDTO,  
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO,
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCCurrentEventPartDetailsDTO,
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO) throws Exception {

		log.info("\n Entering method - compareCurrentAndPreviousEventForMultipleChangeIndicator() in "+CLASS_NAME);
			EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO;
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
			EnterACCSuppSummaryACCCostDataDTO enterACCSuppSummaryACCCostDataDTO;
			EnterACCSuppSummaryACCDataDTO enterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lenterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList=null;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lEnterACCSuppSummaryACCDataDTO;
			BigDecimal m_decTotalACC = new BigDecimal(0.0000);
			BigDecimal m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
			EnterACCEventPartDetailsDTO previousEventPartDetails;
			for(EnterACCEventPartDetailsDTO currentEventPartDetails : m_lEnterACCCurrentEventPartDetailsDTO){
				if(!currentEventPartDetails.isM_bolMatchDone()){
					String strMultipleIndicatorChangeIdentifier=null;
					previousEventPartDetails = new EnterACCEventPartDetailsDTO();
					//this methods checks if there is a match between current and prev due to multiple indicator change
					strMultipleIndicatorChangeIdentifier = compareCurrentAndPreviousPartDataMultipleHierarchy(enterACCApplicationsSuppMTOSummaryDVO,currentEventPartDetails, 
							previousEventPartDetails, m_lEnterACCPreviousEventPartDetailsDTO);
					
					if(null!=strMultipleIndicatorChangeIdentifier){
						//Apply Rules 1,3 and 4 only if there is a supplier change and [Proc group and/or Design section change]
						String[] strRuleACC = null;
						
						//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
							if(strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
									BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value()
									+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()) ||
									strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()
											+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value()) ||
											strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
													BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value()
													+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()
													+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value())){
								strRuleACC = assignACCBasedOnRules(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, null);
								if(strRuleACC==null)
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails, null);
								if(strRuleACC==null)
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails, null);
							}
							//Apply Rule 2 only if there is a Qty and/or Share rate and no Supplier change
							else if(!strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value())
									&& (strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value())||
											strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value()))){
								strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
							}
						//}
						
						
						
						char indicators[] = strMultipleIndicatorChangeIdentifier.toCharArray(); 
						Arrays.sort(indicators);
						String changeIndicatorToShowBasedOnHierarchy = String.valueOf(indicators[0]);
						ArrayList<String> lstIndicators = new ArrayList<String>();
						for(char indicator : indicators){
							lstIndicators.add(String.valueOf(indicator));
						}

						//Match Done hence mark the previous events record as done irrespective of the further validation
						//For previous match done is being set in the method compareCurrentAndPreviousPartDataMultipleHierarchy()
						currentEventPartDetails.setM_bolMatchDone(true);
						log.info("\n current part no multi Indicator - "+currentEventPartDetails.getM_strPartNumber());
						//***************Previous Code Block START***********************
						//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
						//get the ACC from the data base
						//send indicators based on which the logic in the method will be written
						m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO, 
								currentEventPartDetails, previousEventPartDetails, lstIndicators, "BASE");								
						m_decTotalACC = new BigDecimal(0.0000);
						m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
						if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
							log.info("approved acc exists for current part no multi Indicator base"); 
							//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
							//and even if Variance exists add one more ACC data and mark ACC data as pending
							//If variance is not present after ACC is applied consider record as resolved balance
							
							//Main Part Details Data Object
							enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
									previousEventPartDetails.getM_strProcSectCode(),
									previousEventPartDetails.getM_strSupplierNumber(),
									previousEventPartDetails.getM_strSupplierName(),
									previousEventPartDetails.getM_strPlantLocCode(),
									previousEventPartDetails.getM_strPartSectionCode(),
									previousEventPartDetails.getM_strModelCatCode(),
									previousEventPartDetails.getM_decShareRatePercent(),
									previousEventPartDetails.getM_intPartQty(),
									previousEventPartDetails.getM_strPartColorCode(),
									previousEventPartDetails.getM_strPartNumber(),
									previousEventPartDetails.getM_strPartName(),
									BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
									+(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
											.equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DISPLAYED_ON_SCREEN.PROC_GROUP_CHANGE.value()) ?
											" "+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode() : ""),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
									);
							
							enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
							enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
							
							//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
							if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
								m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
							}
							enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
							for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
									List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOSForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO
											,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, lstIndicators, "BASE" );
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("RULE_ID"),
												((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												String.valueOf((Integer)accData.get("ACC_STATUS")),
												"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
												(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
													!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
												&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
														? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									
									//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								}
								
								//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
								if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
								}
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_strAppCostChangeCode(),
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(
												rawACCData.getM_strAccComments(), 
												rawACCData.getM_strAccCommentDesc(), 
												rawACCData.getM_strAccCommentNote()),
										rawACCData.getM_strAccStatus(),
										rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
										rawACCData.getM_strAccRulePartCharMatch(),
										rawACCData.getM_strEffectiveDate(),
										rawACCData.getM_strModifiedBy(),
										rawACCData.getM_strModifiedDate(),
										rawACCData.getM_strBaseOrCurrentEvent());
								m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
								if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
									m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
								}
								enterACCSuppSummaryACCCostDataDTOList.set(
										fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
										enterACCSuppSummaryACCCostDataDTO);
							}
							
							//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
							if(!(m_decTotalACC.compareTo(findVariance(
									previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
									previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
									previousEventPartDetails.getM_decShareRatePercent())) == 0)){
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										(findVariance(
												previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										(findVariance(
												previousEventPartDetails.getM_decEndCostAmount(),  new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										strRuleACC==null ? "" : strRuleACC[0],//Changed Assign ACC by Rule
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ? "" : strRuleACC[2],//Changed Assign ACC by Rule
										strRuleACC==null ? "" : strRuleACC[1],//Changed Assign ACC by Rule
										m_strDefaultEffectiveDate,
										"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
								
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												new EnterACCSuppSummaryACCCommentsDTO(),
												new EnterACCSuppSummaryACCCommentsDTO(),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
												"")
									);
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
										}
									}
									
								}
								//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
								if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								} else {
									enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
								}
							}
							
							BigDecimal balanceCost = ((findVariance(
									previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
									previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
									previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
							//Display data on screen based on the what user has selected in the DataToDisplay field. 
							/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
									&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
									|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
								){*/
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
										new BigDecimal(0.0000),
										new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
												previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
												, previousEventPartDetails.getM_decMCCAmount())),
										new BigDecimal(0.0000)/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent())*/,//TODO - Commented code here and below - MCC amount considered as 0.0000 but in case required to consider the actual MCC the need to uncomments.
										enterACCSuppSummaryACCCostDataDTOList,
										/*(((findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()))
												.subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
														previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount())))
														.subtract(findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
																currentEventPartDetails.getM_decShareRatePercent()))).subtract(m_decTotalACC)*/
										balanceCost,
										femdDTO
										);
								
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							/*} else {
								//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
								if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
									m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
								}
							}*/
							
						} else {
							//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
							//Check if variance exist
							if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
								&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
										previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), //TODO - MCC passed as 0 as in previous MCC is not subtracted.
									previousEventPartDetails.getM_decShareRatePercent()))
									.compareTo(BigDecimal.ZERO) == 0)){
								log.info("no approved acc exists for current part no base ");
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										previousEventPartDetails.getM_strProcSectCode(),
										previousEventPartDetails.getM_strSupplierNumber(),
										previousEventPartDetails.getM_strSupplierName(),
										previousEventPartDetails.getM_strPlantLocCode(),
										previousEventPartDetails.getM_strPartSectionCode(),
										previousEventPartDetails.getM_strModelCatCode(),
										previousEventPartDetails.getM_decShareRatePercent(),
										previousEventPartDetails.getM_intPartQty(),
										previousEventPartDetails.getM_strPartColorCode(),
										previousEventPartDetails.getM_strPartNumber(),
										previousEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
										+(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
											.equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DISPLAYED_ON_SCREEN.PROC_GROUP_CHANGE.value()) ?
													" "+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode() : ""),
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
										);
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,
											m_strDefaultEffectiveDate,
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											"",
											strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
										);
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
								}
								
								//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
												));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
										}
									}
									
								}
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()),
										findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()),
										strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
										strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
										m_strDefaultEffectiveDate,"",""
										, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
								
								//List of ACC Data
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
								for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
									accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									accCostData.setM_decACCCost(new BigDecimal(0.0000));
									accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
									accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
									accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
									enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
								}
								enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
												previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
												, previousEventPartDetails.getM_decMCCAmount()),
										new BigDecimal(0.0000),
										new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
												previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
												, previousEventPartDetails.getM_decMCCAmount())),
										/*findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent())*/
										new BigDecimal(0.0000),
										enterACCSuppSummaryACCCostDataDTOList,
										findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()),
										femdDTO
										);
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							}
						}
						
						
						
						
						//***************Previous Code Block END**************************
						
						
						
						//***************Current Code Block START**************************
						
						//get ACC Cost only if user selected Resolved or Both Resolved & Unresolved balances and also fetch ACC which are pending in case user has selected Unresolved balance.
						//get the ACC from the data base
						m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO, 
								currentEventPartDetails, previousEventPartDetails, lstIndicators, "CURRENT");								
						m_decTotalACC = new BigDecimal(0.0000);
						m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
						if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
							//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
							//and even if Variance exists add one more ACC data and mark ACC data as pending
							//If variance is not present after ACC is applied consider record as resolved balance
							log.info("approved acc exists for prev part no multi indicator current same");
							//Main Part Details Data Object
							enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
									currentEventPartDetails.getM_strProcSectCode(),
									currentEventPartDetails.getM_strSupplierNumber(),
									currentEventPartDetails.getM_strSupplierName(),
									currentEventPartDetails.getM_strPlantLocCode(),
									currentEventPartDetails.getM_strPartSectionCode(),
									currentEventPartDetails.getM_strModelCatCode(),
									currentEventPartDetails.getM_decShareRatePercent(),
									currentEventPartDetails.getM_intPartQty(),
									previousEventPartDetails.getM_strPartColorCode(),
									currentEventPartDetails.getM_strPartNumber(),
									currentEventPartDetails.getM_strPartName(),
									BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
									+(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
											.equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DISPLAYED_ON_SCREEN.PROC_GROUP_CHANGE.value()) ?
													" "+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode() : ""),
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
									);
							
							enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
							enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
							
							//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
							if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
								m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
							}
							enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
							for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
									List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOSForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO
											,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, lstIndicators, "CURRENT" );
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("RULE_ID"),
												((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												String.valueOf((Integer)accData.get("ACC_STATUS")),
												"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
												(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
														String.valueOf((Integer)accData.get("ACC_STATUS")) ,
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
														(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
																? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									
									//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									
								}
								
								//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
								if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
								}
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_strAppCostChangeCode(),
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(
												rawACCData.getM_strAccComments(), 
												rawACCData.getM_strAccCommentDesc(), 
												rawACCData.getM_strAccCommentNote()),
										rawACCData.getM_strAccStatus(),
										rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
										rawACCData.getM_strAccRulePartCharMatch(),
										rawACCData.getM_strEffectiveDate(),
										rawACCData.getM_strModifiedBy(),
										rawACCData.getM_strModifiedDate(),
										rawACCData.getM_strBaseOrCurrentEvent());
								m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
								if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
									m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
								}
								int location = fetchLocationToAddACCInList(
								        m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO),
								        rawACCData);

								if(location < enterACCSuppSummaryACCCostDataDTOList.size()){
								    enterACCSuppSummaryACCCostDataDTOList.set(
								            location,
								            enterACCSuppSummaryACCCostDataDTO);
								} else {

								    while(enterACCSuppSummaryACCCostDataDTOList.size() < location){
								        EnterACCSuppSummaryACCCostDataDTO dummyACC =
								                new EnterACCSuppSummaryACCCostDataDTO();

								        dummyACC.setM_decACCCost(new BigDecimal("0.0000"));
								        dummyACC.setM_decOriginalACCCost(new BigDecimal("0.0000"));
								        dummyACC.setM_strAccStatus(
								                BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);

								        enterACCSuppSummaryACCCostDataDTOList.add(dummyACC);
								    }

								    enterACCSuppSummaryACCCostDataDTOList.add(
								            enterACCSuppSummaryACCCostDataDTO);
								}
							}
							
							//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
							if(!(m_decTotalACC.compareTo(findVariance(
									new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
									new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
									currentEventPartDetails.getM_decShareRatePercent())) == 0)){
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										(findVariance(
												new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										(findVariance(
												new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
										strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
										m_strDefaultEffectiveDate,
										"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
								
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													""));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
										}
									}
									
								}
								//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
								if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								} else {
									enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
								}
							}
							BigDecimal balanceCost = ((findVariance(
									new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
									new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
									currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
							//Display data on screen based on the what user has selected in the DataToDisplay field. 
							/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
									&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
									|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
								){*/
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										new BigDecimal(0.0000),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
										findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent()),
										enterACCSuppSummaryACCCostDataDTOList,
										balanceCost,
										femdDTO
										);
								
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							/*} else {
								//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
								if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
									m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
								}
							}*/
							
						} else {
							//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
							//Check if variance exist
							if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
									new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
									currentEventPartDetails.getM_decShareRatePercent()))
									.compareTo(BigDecimal.ZERO) == 0)){
								log.info("no approved acc exists multi indicator current ");
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										currentEventPartDetails.getM_strProcSectCode(),
										currentEventPartDetails.getM_strSupplierNumber(),
										currentEventPartDetails.getM_strSupplierName(),
										currentEventPartDetails.getM_strPlantLocCode(),
										currentEventPartDetails.getM_strPartSectionCode(),
										currentEventPartDetails.getM_strModelCatCode(),
										currentEventPartDetails.getM_decShareRatePercent(),
										currentEventPartDetails.getM_intPartQty(),
										currentEventPartDetails.getM_strPartColorCode(),
										currentEventPartDetails.getM_strPartNumber(),
										currentEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
										+(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR_REASON_DB_TO_SCREEN_MAP.get(changeIndicatorToShowBasedOnHierarchy)
											.equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DISPLAYED_ON_SCREEN.PROC_GROUP_CHANGE.value()) ?
													" "+previousEventPartDetails.getM_strProcSectCode()+" to "+currentEventPartDetails.getM_strProcSectCode() : ""),
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
										);
								
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,
											m_strDefaultEffectiveDate,
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											"",
											strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
										);
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
								}
								
								//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
												));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
										}
									}
									
								}
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
										strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
										m_strDefaultEffectiveDate,"","",
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
								
								//List of ACC Data
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
								for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
									accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									accCostData.setM_decACCCost(new BigDecimal(0.0000));
									accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
									accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
									accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
									enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
								}
								enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										new BigDecimal(0.0000),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
												currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
												, currentEventPartDetails.getM_decMCCAmount()),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
												currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
												, currentEventPartDetails.getM_decMCCAmount()),
										findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										enterACCSuppSummaryACCCostDataDTOList,
										findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										femdDTO
										);
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							}
						}
						//***************Current Code Block END**************************
					
					}
				}
			}
			
		log.info("\n Exiting method - compareCurrentAndPreviousEventForMultipleChangeIndicator() in "+CLASS_NAME);
	
	}
	
	private void compareCurrentAndPreviousEventForProcChange(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			EnterACCSuppFEMDMTODTO femdDTO,  
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO,
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCCurrentEventPartDetailsDTO,
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO) {
		log.info("\n Entering method - compareCurrentAndPreviousEventForProcChange() in "+CLASS_NAME);
			EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO;
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
			EnterACCSuppSummaryACCCostDataDTO enterACCSuppSummaryACCCostDataDTO;
			EnterACCSuppSummaryACCDataDTO enterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lenterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList=null;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lEnterACCSuppSummaryACCDataDTO;
			BigDecimal m_decTotalACC = new BigDecimal(0.0000);
			BigDecimal m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
			
			if(null != m_lEnterACCCurrentEventPartDetailsDTO){
				EnterACCEventPartDetailsDTO previousEventPartDetails;
				
				for(EnterACCEventPartDetailsDTO currentEventPartDetails : m_lEnterACCCurrentEventPartDetailsDTO){
					if(!currentEventPartDetails.isM_bolMatchDone()){
						
						//Check if we have this part in the previous event but with different Proc Sect Code
						String[] returnParam = accProcessingBatchDAO.checkifProcSectionIsChanged(enterACCApplicationsSuppMTOSummaryDVO, 
								currentEventPartDetails, "CURRENT", femdDTO);
						String procSect = returnParam[0];
						String suppNo = returnParam[1];
						if(!StringUtils.equals("", procSect)){
							currentEventPartDetails.setM_bolMatchDone(true);
							log.info("compareCurrentAndPreviousEventForProcChange current part no - "+currentEventPartDetails.getM_strPartNumber());
							previousEventPartDetails = new EnterACCEventPartDetailsDTO();
							previousEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
							previousEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
							previousEventPartDetails.setM_strProcSectCode(procSect);
							
							//Check if there is Qty, Share rate and design section change based on which Rules need to be decided
							accProcessingBatchDAO.checkQtyShareRateDesignSectChangeForHierarchy(enterACCApplicationsSuppMTOSummaryDVO, 
									previousEventPartDetails, currentEventPartDetails, "CURRENT", femdDTO);
							ArrayList<String> lstIndicators = new ArrayList<String>();
							String strMultipleIndicatorChangeIdentifier = BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value();
							lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value());
							
							if(!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value());
							}
							
							if(previousEventPartDetails.getM_intPartQty()!=null&&
									!(previousEventPartDetails.getM_intPartQty()==currentEventPartDetails.getM_intPartQty())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value());
							}
							
							if(previousEventPartDetails.getM_decShareRatePercent()!=null&&
									!(previousEventPartDetails.getM_decShareRatePercent().compareTo(currentEventPartDetails.getM_decShareRatePercent()) == 0)){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value());
							}
							
							if(previousEventPartDetails.getM_strPartSectionCode()!=null&&
									!previousEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(currentEventPartDetails.getM_strPartSectionCode())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value());
							}
							
							if(previousEventPartDetails.getM_strPartColorCode()!=null&&
									!previousEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(currentEventPartDetails.getM_strPartColorCode())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value());
							}
							
							//Apply Rules 1,3 and 4 only if there is a supplier change and [Proc group and/or Design section change]
							String[] strRuleACC = null;
							
							//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
								if(strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value()
										+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()) ||
										strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value()
												+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()
												+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value())){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, null);
									if(strRuleACC==null)
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails, null);
									if(strRuleACC==null)
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails, null);
								}
								//Apply Rule 2 only if there is a Qty and/or Share rate and no Supplier change
								else if(!strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value())
										&& (strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value())||
												strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value()))){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
								}
							//}
							
							//Check if ACC is present for this current and previous.
							//get the ACC from the data base
							m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForProcChangePartAddedDropped(enterACCApplicationsSuppMTOSummaryDVO,
									currentEventPartDetails, femdDTO, "CURRENT_SAME");
							
							m_decTotalACC = new BigDecimal(0.0000);
							m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
							
							if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
								log.info("acc found for current part no compareCurrentAndPreviousEventForProcChange");
								//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
								//and even if Variance exists add one more ACC data and mark ACC data as pending
								//If variance is not present after ACC is applied consider record as resolved balance
								
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										currentEventPartDetails.getM_strProcSectCode(),
										currentEventPartDetails.getM_strSupplierNumber(),
										currentEventPartDetails.getM_strSupplierName(),
										currentEventPartDetails.getM_strPlantLocCode(),
										currentEventPartDetails.getM_strPartSectionCode(),
										currentEventPartDetails.getM_strModelCatCode(),
										currentEventPartDetails.getM_decShareRatePercent(),
										currentEventPartDetails.getM_intPartQty(),
										currentEventPartDetails.getM_strPartColorCode(),
										currentEventPartDetails.getM_strPartNumber(),
										currentEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
										+procSect+" to "+currentEventPartDetails.getM_strProcSectCode(),
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
										);
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
									
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
										//List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
										//		,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, currentEventPartDetails, "PROC_GROUP_CHANGE_MATCH", "CURRENT_SAME" );
										
										List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOSForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO
												,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, lstIndicators, "CURRENT_SAME" );
										
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										for(Map<String,Object> accData : allACCs){
											//ACC Cost Data - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(String)accData.get("RULE_ID"),
													((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
													Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
													Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
													String.valueOf((Integer)accData.get("ACC_STATUS")),
													"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
													(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										}
										
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										
										for(Map<String,Object> accData : allACCs){
											//ACC Cost Data - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															(String)accData.get("ACC_COMMENTS"), 
															(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																	(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
													    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
													new EnterACCSuppSummaryACCCommentsDTO(
															(String)accData.get("ACC_COMMENTS"), 
															(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																	(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
													    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
													String.valueOf((Integer)accData.get("ACC_STATUS")) ,
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
														? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										}
										
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										
										//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									}
									
									//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
									if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
									}
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											rawACCData.getM_decACCAmount(),
											rawACCData.getM_decACCAmount(),
											rawACCData.getM_strAppCostChangeCode(),
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													rawACCData.getM_strAccComments(), 
													rawACCData.getM_strAccCommentDesc(), 
													rawACCData.getM_strAccCommentNote()),
											rawACCData.getM_strAccStatus(),
											rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
											rawACCData.getM_strAccRulePartCharMatch(),
											rawACCData.getM_strEffectiveDate(),
											rawACCData.getM_strModifiedBy(),
											rawACCData.getM_strModifiedDate(),
											rawACCData.getM_strBaseOrCurrentEvent());
									m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
									if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
										m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
									}
									enterACCSuppSummaryACCCostDataDTOList.set(
											fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
											enterACCSuppSummaryACCCostDataDTO);
								}
								
								//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
								if(!(m_decTotalACC.compareTo(findVariance(
										new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
										new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent())) == 0)){
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											(findVariance(
													new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											(findVariance(
													new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											strRuleACC==null ? "" : strRuleACC[0],//Changed Assign ACC by Rule
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ? "" : strRuleACC[2],//Changed Assign ACC by Rule
											strRuleACC==null ? "" : strRuleACC[1],//Changed Assign ACC by Rule"",
											m_strDefaultEffectiveDate,
											"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
									
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
									
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													"")
										);
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
											}
										}
										
									}
									//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
									if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									} else {
										enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
									}
								}
								BigDecimal balanceCost = ((findVariance(
										new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
										new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
								//Display data on screen based on the what user has selected in the DataToDisplay field. 
								/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
										&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
												&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
										|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											new BigDecimal(0.0000),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
											findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
													currentEventPartDetails.getM_decShareRatePercent()),
											enterACCSuppSummaryACCCostDataDTOList,
											balanceCost,
											femdDTO
											);
									
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								/*} else {
									//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
									if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
										m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
									}
								}*/
								
							} else{
								//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
								//Check if variance exist
								if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
										new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
										currentEventPartDetails.getM_decShareRatePercent()))
										.compareTo(BigDecimal.ZERO) == 0)){
									log.info("No appr acc found compareCurrentAndPreviousEventForProcChange current same");
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											currentEventPartDetails.getM_strProcSectCode(),
											currentEventPartDetails.getM_strSupplierNumber(),
											currentEventPartDetails.getM_strSupplierName(),
											currentEventPartDetails.getM_strPlantLocCode(),
											currentEventPartDetails.getM_strPartSectionCode(),
											currentEventPartDetails.getM_strModelCatCode(),
											currentEventPartDetails.getM_decShareRatePercent(),
											currentEventPartDetails.getM_intPartQty(),
											currentEventPartDetails.getM_strPartColorCode(),
											currentEventPartDetails.getM_strPartNumber(),
											currentEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
											+procSect+" to "+currentEventPartDetails.getM_strProcSectCode(),
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
									
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													);
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(
																"", 
																"", 
																""),
														new EnterACCSuppSummaryACCCommentsDTO(
																"", 
																"", 
																""),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									}
									
									//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													));
									
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""
													));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
											}
										}
										
									}
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule,
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule,
											m_strDefaultEffectiveDate,"",""
											, BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
									
									//List of ACC Data
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											new BigDecimal(0.0000),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
													currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
													, currentEventPartDetails.getM_decMCCAmount()),
											findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
													currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
													, currentEventPartDetails.getM_decMCCAmount()),
											findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											enterACCSuppSummaryACCCostDataDTOList,
											findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
													new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
													currentEventPartDetails.getM_decShareRatePercent()),
											femdDTO
											);
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								}
							}
							
						}
					}
				}
			}
			
			
			if(null != m_lEnterACCPreviousEventPartDetailsDTO){
				EnterACCEventPartDetailsDTO currentEventPartDetails;
				for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
	
					if(!previousEventPartDetails.isM_bolMatchDone()){
						//Check if we have this part in the previous event but with different Proc Sect Code
						String[] returnParam = accProcessingBatchDAO.checkifProcSectionIsChanged(enterACCApplicationsSuppMTOSummaryDVO, 
								previousEventPartDetails, "PREVIOUS", femdDTO);
						String procSect = returnParam[0];
						String suppNo = returnParam[1];
						if(!StringUtils.equals("", procSect)){
							previousEventPartDetails.setM_bolMatchDone(true);
							log.info("prev part no compareCurrentAndPreviousEventForProcChange - "+previousEventPartDetails.getM_strPartNumber());
							currentEventPartDetails = new EnterACCEventPartDetailsDTO();
							currentEventPartDetails.setM_strPartNumber(previousEventPartDetails.getM_strPartNumber());
							currentEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : previousEventPartDetails.getM_strSupplierNumber());
							currentEventPartDetails.setM_strProcSectCode(procSect);
							
							//Check if there is Qty, Share rate and design section change based on which Rules need to be decided
							accProcessingBatchDAO.checkQtyShareRateDesignSectChangeForHierarchy(enterACCApplicationsSuppMTOSummaryDVO, 
									previousEventPartDetails, currentEventPartDetails, "PREVIOUS", femdDTO);
							ArrayList<String> lstIndicators = new ArrayList<String>();
							String strMultipleIndicatorChangeIdentifier = BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value();
							lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value());
							
							if(!currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value());
							}
							
							if(currentEventPartDetails.getM_intPartQty()!=null&&
									!(currentEventPartDetails.getM_intPartQty()==previousEventPartDetails.getM_intPartQty())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value());
							}
							
							if(currentEventPartDetails.getM_decShareRatePercent()!=null&&
									!(currentEventPartDetails.getM_decShareRatePercent().compareTo(previousEventPartDetails.getM_decShareRatePercent()) == 0)){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value());
							}
							
							if(currentEventPartDetails.getM_strPartSectionCode()!=null&&
									!currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value());
							}
							
							if(currentEventPartDetails.getM_strPartColorCode()!=null&&
									!currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode())){
								strMultipleIndicatorChangeIdentifier = strMultipleIndicatorChangeIdentifier + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value();
								lstIndicators.add(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value());
							}
							
							//Apply Rules 1,3 and 4 only if there is a supplier change and [Proc group and/or Design section change]
							String[] strRuleACC = null;
							
							//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
								if(strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value()
										+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()) ||
										strMultipleIndicatorChangeIdentifier.equalsIgnoreCase(
												BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value()
												+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()
												+BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value())){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, null);
									if(strRuleACC==null)
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails, null);
									if(strRuleACC==null)
										strRuleACC = assignACCBasedOnRules(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails, null);
								}
								//Apply Rule 2 only if there is a Qty and/or Share rate and no Supplier change
								else if(!strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value())
										&& (strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value())||
												strMultipleIndicatorChangeIdentifier.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value()))){
									strRuleACC = assignACCBasedOnRules(AccRuleEnum.FSTN, previousEventPartDetails, currentEventPartDetails, null);
								}
							//}
							
							//Check if ACC is present for this current and previous.
							//get the ACC from the data base
							m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForProcChangePartAddedDropped(enterACCApplicationsSuppMTOSummaryDVO,
									previousEventPartDetails, femdDTO, "BASE");
							
							m_decTotalACC = new BigDecimal(0.0000);
							m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
							
							if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
								log.info("acc found prev part no compareCurrentAndPreviousEventForProcChange base");
								//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
								//and even if Variance exists add one more ACC data and mark ACC data as pending
								//If variance is not present after ACC is applied consider record as resolved balance
								
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										previousEventPartDetails.getM_strProcSectCode(),
										previousEventPartDetails.getM_strSupplierNumber(),
										previousEventPartDetails.getM_strSupplierName(),
										previousEventPartDetails.getM_strPlantLocCode(),
										previousEventPartDetails.getM_strPartSectionCode(),
										previousEventPartDetails.getM_strModelCatCode(),
										previousEventPartDetails.getM_decShareRatePercent(),
										previousEventPartDetails.getM_intPartQty(),
										previousEventPartDetails.getM_strPartColorCode(),
										previousEventPartDetails.getM_strPartNumber(),
										previousEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
										+previousEventPartDetails.getM_strProcSectCode()+" to "+procSect,
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
										);
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
									
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
										//List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
										//		,enterACCSuppSummaryPartLevelDataDTO, previousEventPartDetails, previousEventPartDetails, "PROC_GROUP_CHANGE_MATCH", "BASE" );
										
										List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOSForMultipleIndicatorChange(enterACCApplicationsSuppMTOSummaryDVO
												,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, previousEventPartDetails, lstIndicators, "BASE" );
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										for(Map<String,Object> accData : allACCs){
											//ACC Cost Data - Effective Date and Rule ID.
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(String)accData.get("RULE_ID"),
													((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
													Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
													Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
													String.valueOf((Integer)accData.get("ACC_STATUS")),
													"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
													(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										}
										
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										
										for(Map<String,Object> accData : allACCs){
											//ACC Cost Data - ACC, Comments and Status
											enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
													(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
													false,
													new EnterACCSuppSummaryACCCommentsDTO(
															(String)accData.get("ACC_COMMENTS"), 
															(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																	(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
													    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
													new EnterACCSuppSummaryACCCommentsDTO(
															(String)accData.get("ACC_COMMENTS"), 
															(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																	(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
													    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
													String.valueOf((Integer)accData.get("ACC_STATUS")) ,
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
														!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
													(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
														&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
														? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
											enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										}
										
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
										
										//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									}
									
									//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
									if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
										EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
										for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
											accCostData = new EnterACCSuppSummaryACCCostDataDTO();
											accCostData.setM_decACCCost(new BigDecimal(0.0000));
											accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
											accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
											accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
											enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
										}
									}
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											rawACCData.getM_decACCAmount(),
											rawACCData.getM_decACCAmount(),
											rawACCData.getM_strAppCostChangeCode(),
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													rawACCData.getM_strAccComments(), 
													rawACCData.getM_strAccCommentDesc(), 
													rawACCData.getM_strAccCommentNote()),
											rawACCData.getM_strAccStatus(),
											rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
											rawACCData.getM_strAccRulePartCharMatch(),
											rawACCData.getM_strEffectiveDate(),
											rawACCData.getM_strModifiedBy(),
											rawACCData.getM_strModifiedDate(),
											rawACCData.getM_strBaseOrCurrentEvent());
									m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
									if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
										m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
									}
									enterACCSuppSummaryACCCostDataDTOList.set(
											fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
											enterACCSuppSummaryACCCostDataDTO);
								}
								
								//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
								if(!(m_decTotalACC.compareTo(findVariance( previousEventPartDetails.getM_decEndCostAmount(),
										new BigDecimal(0.0000),previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
										previousEventPartDetails.getM_decShareRatePercent())) == 0)){
									
									//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
									//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
									
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											(findVariance( previousEventPartDetails.getM_decEndCostAmount(),
													new BigDecimal(0.0000), 
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											(findVariance( previousEventPartDetails.getM_decEndCostAmount(),
													new BigDecimal(0.0000), 
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()))
												.subtract(m_decTotalACC),
											strRuleACC==null ? "" : strRuleACC[0],//Changed Assign ACC by Rule
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ? "" : strRuleACC[2],//Changed Assign ACC by Rule
											strRuleACC==null ? "" : strRuleACC[1],//Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,
											"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
									
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
													m_strDefaultEffectiveDate,
													m_strDefaultEffectiveDate,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													"",
													strRuleACC==null ? "" : strRuleACC[3]));
									
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													"")
										);
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
											}
										}
										
									}
									//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
									if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
										enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									} else {
										enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
									}
								}
								BigDecimal balanceCost = ((findVariance( previousEventPartDetails.getM_decEndCostAmount(),
										new BigDecimal(0.0000), 
										previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
										previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
								//Display data on screen based on the what user has selected in the DataToDisplay field. 
								/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
										&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
												&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
										|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
									){*/
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
													previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
											new BigDecimal(0.0000),
											new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount())),
											new BigDecimal(0.0000),
											enterACCSuppSummaryACCCostDataDTOList,
											balanceCost,
											femdDTO
											);
									
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								/*} else {
									//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
									if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
										m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
									}
								}*/
								
							} else{
								//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
								//Check if variance exist
								if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
										&& */!((findVariance(  previousEventPartDetails.getM_decEndCostAmount(),new BigDecimal(0.0000), 
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
										previousEventPartDetails.getM_decShareRatePercent()))
										.compareTo(BigDecimal.ZERO) == 0)){
									log.info("acc not found for prev part no compareCurrentAndPreviousEventForProcChange base");
									//Main Part Details Data Object
									enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
											previousEventPartDetails.getM_strProcSectCode(),
											previousEventPartDetails.getM_strSupplierNumber(),
											previousEventPartDetails.getM_strSupplierName(),
											previousEventPartDetails.getM_strPlantLocCode(),
											previousEventPartDetails.getM_strPartSectionCode(),
											previousEventPartDetails.getM_strModelCatCode(),
											previousEventPartDetails.getM_decShareRatePercent(),
											previousEventPartDetails.getM_intPartQty(),
											previousEventPartDetails.getM_strPartColorCode(),
											previousEventPartDetails.getM_strPartNumber(),
											previousEventPartDetails.getM_strPartName(),
											BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PROC_GROUP_CHANGE.value)+" "
											+previousEventPartDetails.getM_strProcSectCode()+" to "+procSect,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
											);
									enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
									enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
									//Check the acc seq and arrange the ACC fetched accordingly.
									if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										
										//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
										//which includes the ACC drop down 
										//and left of this we display Effective date and rule id so creating one more object for the same.
										m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										
										//List of ACCs seq - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												"",
												strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
												);
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
												"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										//List of ACCs seq - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
												strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												new EnterACCSuppSummaryACCCommentsDTO(
														"", 
														"", 
														""),
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
												BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
										
										enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
										enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
												"Previous",
												"Current",
												"Difference",
												"MCC",
												"Balance",
												enterACCSuppSummaryACCCostDataDTOList
												);
										m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										
										m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									}
									
									//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
									int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
									
									if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
														m_strDefaultEffectiveDate,
														m_strDefaultEffectiveDate,
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														"",
														strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
													));
									
										m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
														false,
														new EnterACCSuppSummaryACCCommentsDTO(),
														new EnterACCSuppSummaryACCCommentsDTO(),
														strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
																: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
														BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""
													));
										
										//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
										if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
												 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
											for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
												m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
												.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
														new EnterACCSuppSummaryACCCostDataDTO(
																new BigDecimal(0.0000),
																new BigDecimal(0.0000),
																"",
																false,
																false,
																new EnterACCSuppSummaryACCCommentsDTO(),
																BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
																"",
																"",
																m_strDefaultEffectiveDate,
																"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
											}
										}
										
									}
									
									//ACC Cost Data
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),  
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),  
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											false,
											false,
											new EnterACCSuppSummaryACCCommentsDTO(),
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
											m_strDefaultEffectiveDate,"","",
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
									
									//List of ACC Data
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
									
									//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
									//Adding the ACC Cost in the object
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
											findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount()),
											new BigDecimal(0.0000),
											new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
													previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
													, previousEventPartDetails.getM_decMCCAmount())),
											new BigDecimal(0.0000),
											enterACCSuppSummaryACCCostDataDTOList,
											findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
													previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
													previousEventPartDetails.getM_decShareRatePercent()),
											femdDTO
											);
									
									//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
									if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
										m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
									}
									if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
									} else {
										//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
										m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
										m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
										m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
									}
								}
							}
							
						}
					}
				}
				
			}
			
			
		log.info("\n Exiting method - compareCurrentAndPreviousEventForProcChange() in "+CLASS_NAME);
	}
	
	private void compareCurrentAndPreviousEventForAddedDroppedParts(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			EnterACCSuppFEMDMTODTO femdDTO,  
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO,
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCCurrentEventPartDetailsDTO,
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO,
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO) {
		log.info("\n Entering method - compareCurrentAndPreviousEventForAddedDroppedParts() in "+CLASS_NAME);
			EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO;
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
			EnterACCSuppSummaryACCCostDataDTO enterACCSuppSummaryACCCostDataDTO;
			EnterACCSuppSummaryACCDataDTO enterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lenterACCSuppSummaryACCDataDTO;
			ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList=null;
			ArrayList<EnterACCSuppSummaryACCDataDTO> m_lEnterACCSuppSummaryACCDataDTO;
			BigDecimal m_decTotalACC = new BigDecimal(0.0000);
			BigDecimal m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
			if(null!=m_lEnterACCCurrentEventPartDetailsDTO&&!m_lEnterACCCurrentEventPartDetailsDTO.isEmpty()){
				for(EnterACCEventPartDetailsDTO currentEventPartDetails : m_lEnterACCCurrentEventPartDetailsDTO){
					
					EnterACCEventPartDetailsDTO previousEventPartDetails = new EnterACCEventPartDetailsDTO();
					if(!currentEventPartDetails.isM_bolMatchDone()){
						
						currentEventPartDetails.setM_bolMatchDone(true);

						log.info("current event part details - "+currentEventPartDetails.getM_strPartNumber()+" "+currentEventPartDetails.getM_strSupplierNumber());
						String[] strRuleACC = null;
						//INC0726363  / CPT-357 - show part dropped or added with rules applied
						
						//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
							strRuleACC = assignACCBasedOnRulesPartialPartMatch(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, enterACCApplicationsSuppMTOSummaryDVO, femdDTO, "CURRENT");
							if(strRuleACC==null)
								strRuleACC = assignACCBasedOnRulesPartialPartMatch(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails,  enterACCApplicationsSuppMTOSummaryDVO, femdDTO, "CURRENT");
							if(strRuleACC==null)
								strRuleACC = assignACCBasedOnRulesPartialPartMatch(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails,  enterACCApplicationsSuppMTOSummaryDVO, femdDTO, "CURRENT");
							//INC0726363  / CPT-357 -  end
						//}
						
						
						//Check if ACC is present for this current and previous.
						//get the ACC from the data base
						m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForProcChangePartAddedDropped(enterACCApplicationsSuppMTOSummaryDVO,
								currentEventPartDetails, femdDTO, "CURRENT_SAME");
						
						m_decTotalACC = new BigDecimal(0.0000);
						m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
						
						if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
							//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
							//and even if Variance exists add one more ACC data and mark ACC data as pending
							//If variance is not present after ACC is applied consider record as resolved balance
							
							//Main Part Details Data Object
							log.info("Approved acc found in PartAddedDropped current same");
							enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
									currentEventPartDetails.getM_strProcSectCode(),
									currentEventPartDetails.getM_strSupplierNumber(),
									currentEventPartDetails.getM_strSupplierName(),
									currentEventPartDetails.getM_strPlantLocCode(),
									currentEventPartDetails.getM_strPartSectionCode(),
									currentEventPartDetails.getM_strModelCatCode(),
									currentEventPartDetails.getM_decShareRatePercent(),
									currentEventPartDetails.getM_intPartQty(),
									currentEventPartDetails.getM_strPartColorCode(),
									currentEventPartDetails.getM_strPartNumber(),
									currentEventPartDetails.getM_strPartName(),
									BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_ADDED.value),
									BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
									);
							enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
							enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
							
							//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
							if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
								m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
							}
							enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
							for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
									List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
											,enterACCSuppSummaryPartLevelDataDTO, currentEventPartDetails, currentEventPartDetails, "PART_ADDED", "CURRENT_SAME" );
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("RULE_ID"),
												((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												String.valueOf((Integer)accData.get("ACC_STATUS")),
												"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
												(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
												String.valueOf((Integer)accData.get("ACC_STATUS")) ,
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
													!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "C",
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
												&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
												? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									
									//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								}
								
								//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
								if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
								}
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_strAppCostChangeCode(),
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(
												rawACCData.getM_strAccComments(), 
												rawACCData.getM_strAccCommentDesc(), 
												rawACCData.getM_strAccCommentNote()),
										rawACCData.getM_strAccStatus(),
										rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
										rawACCData.getM_strAccRulePartCharMatch(),
										rawACCData.getM_strEffectiveDate(),
										rawACCData.getM_strModifiedBy(),
										rawACCData.getM_strModifiedDate(),
										rawACCData.getM_strBaseOrCurrentEvent());
								m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
								if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
									m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
								}
								enterACCSuppSummaryACCCostDataDTOList.set(
										fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
										enterACCSuppSummaryACCCostDataDTO);
							}
							
							//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
							if(!(m_decTotalACC.compareTo(findVariance(
									new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
									new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
									currentEventPartDetails.getM_decShareRatePercent())) == 0)){
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										(findVariance(
												new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										(findVariance(
												new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										//"",
										strRuleACC==null ? "" : strRuleACC[0],
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
										//"",
										//"",
										strRuleACC==null ? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value 
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//TODO Changed Assign ACC by Rule,
										strRuleACC==null ? "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
										strRuleACC==null ? "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
										m_strDefaultEffectiveDate,
										"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
								
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(//"",
												//"",
												strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,"",""));
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
												"",
												strRuleACC==null ? "" : strRuleACC[3]));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(//"",
													//"",
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
										}
									}
									
								}
								//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
								if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								} else {
									enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
								}
							}
							BigDecimal balanceCost = ((findVariance(
									new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
									new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
									currentEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
							//Display data on screen based on the what user has selected in the DataToDisplay field. 
							/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
									&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
									|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
								){*/
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										new BigDecimal(0.0000),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent(), currentEventPartDetails.getM_decMCCAmount()),
										findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(),
												currentEventPartDetails.getM_decShareRatePercent()),
										enterACCSuppSummaryACCCostDataDTOList,
										balanceCost,
										femdDTO
										);
								
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							/*} else {
								//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
								if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
									m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
								}
							}*/
							
						} else{
							//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
							//Check if variance exist
							if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									&&*/ !((findVariance( new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
									new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
									currentEventPartDetails.getM_decShareRatePercent()))
									.compareTo(BigDecimal.ZERO) == 0)){
								log.info("No pproved acc found in PartAddedDropped current same");
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										currentEventPartDetails.getM_strProcSectCode(),
										currentEventPartDetails.getM_strSupplierNumber(),
										currentEventPartDetails.getM_strSupplierName(),
										currentEventPartDetails.getM_strPlantLocCode(),
										currentEventPartDetails.getM_strPartSectionCode(),
										currentEventPartDetails.getM_strModelCatCode(),
										currentEventPartDetails.getM_decShareRatePercent(),
										currentEventPartDetails.getM_intPartQty(),
										currentEventPartDetails.getM_strPartColorCode(),
										currentEventPartDetails.getM_strPartNumber(),
										currentEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_ADDED.value),
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT
										);
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(currentEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(currentEventPartDetails.getM_strPartNumber());
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											//"",
											//"",
											strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],
											m_strDefaultEffectiveDate,
											m_strDefaultEffectiveDate,
											//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,"","");
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											"",
											strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
											);
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											//"","",
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											strRuleACC==null ?  "" : strRuleACC[0],
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,"");
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
								}
								
								//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(//"",
												//"",
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
												strRuleACC==null ?  "" : strRuleACC[1],
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,"",""));
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												"",
												strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
											));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(//"",
													//"",
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT,""));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT));
										}
									}
									
								}
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										//"",
										strRuleACC==null ?  "" : strRuleACC[0],
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
										//"",
										//"",
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
										strRuleACC==null ?  "" : strRuleACC[1],
										m_strDefaultEffectiveDate,"","",
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
								
								//List of ACC Data
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
								for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
									accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									accCostData.setM_decACCCost(new BigDecimal(0.0000));
									accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
									accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
									accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_CURRENT);
									enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
								}
								enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										new BigDecimal(0.0000),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
												currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
												, currentEventPartDetails.getM_decMCCAmount()),
										findEndCost(currentEventPartDetails.getM_decEndCostAmount(), 
												currentEventPartDetails.getM_intPartQty(), currentEventPartDetails.getM_decShareRatePercent()
												, currentEventPartDetails.getM_decMCCAmount()),
										findMCCCost(currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										enterACCSuppSummaryACCCostDataDTOList,
										findVariance(new BigDecimal(0.0000), currentEventPartDetails.getM_decEndCostAmount(), 
												new BigDecimal(0.0000), currentEventPartDetails.getM_decMCCAmount(), currentEventPartDetails.getM_intPartQty(), 
												currentEventPartDetails.getM_decShareRatePercent()),
										femdDTO
										);
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							}
						}
							
					}
				}
			}
			if(null!=m_lEnterACCPreviousEventPartDetailsDTO && !m_lEnterACCPreviousEventPartDetailsDTO.isEmpty()){
				for(EnterACCEventPartDetailsDTO previousEventPartDetails : m_lEnterACCPreviousEventPartDetailsDTO){
					
					EnterACCEventPartDetailsDTO currentEventPartDetails = new EnterACCEventPartDetailsDTO();
					if(!previousEventPartDetails.isM_bolMatchDone()){
						previousEventPartDetails.setM_bolMatchDone(true);
						
						log.info("previous event part details - "+previousEventPartDetails.getM_strPartNumber()+" "+previousEventPartDetails.getM_strSupplierNumber());
						
						String[] strRuleACC = null;
						
						//if(!enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
							strRuleACC = assignACCBasedOnRulesPartialPartMatch(AccRuleEnum.EXPN, previousEventPartDetails, currentEventPartDetails, enterACCApplicationsSuppMTOSummaryDVO, femdDTO, "BASE");
							if(strRuleACC==null)
								strRuleACC = assignACCBasedOnRulesPartialPartMatch(AccRuleEnum.NEXP, previousEventPartDetails, currentEventPartDetails,  enterACCApplicationsSuppMTOSummaryDVO, femdDTO, "BASE");
							if(strRuleACC==null)
								strRuleACC = assignACCBasedOnRulesPartialPartMatch(AccRuleEnum.IHOS, previousEventPartDetails, currentEventPartDetails,  enterACCApplicationsSuppMTOSummaryDVO, femdDTO, "BASE");
						//}
						//Check if ACC is present for this current and previous.
						//get the ACC from the data base
						m_lenterACCSuppSummaryACCDataDetailsDTOList = accProcessingBatchDAO.fetchACCDataForProcChangePartAddedDropped(enterACCApplicationsSuppMTOSummaryDVO,
								previousEventPartDetails, femdDTO, "BASE");
						
						m_decTotalACC = new BigDecimal(0.0000);
						m_decTotalACCAppliedByBatch = new BigDecimal(0.0000);
						
						if(null != m_lenterACCSuppSummaryACCDataDetailsDTOList && m_lenterACCSuppSummaryACCDataDetailsDTOList.size()>0){
							//If ACC exists add the ACC data and mark as Pending Approval or ACC Applied 
							//and even if Variance exists add one more ACC data and mark ACC data as pending
							//If variance is not present after ACC is applied consider record as resolved balance
							
							//Main Part Details Data Object
							log.info("Approved acc found in PartAddedDropped base");
							enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
									previousEventPartDetails.getM_strProcSectCode(),
									previousEventPartDetails.getM_strSupplierNumber(),
									previousEventPartDetails.getM_strSupplierName(),
									previousEventPartDetails.getM_strPlantLocCode(),
									previousEventPartDetails.getM_strPartSectionCode(),
									previousEventPartDetails.getM_strModelCatCode(),
									previousEventPartDetails.getM_decShareRatePercent(),
									previousEventPartDetails.getM_intPartQty(),
									previousEventPartDetails.getM_strPartColorCode(),
									previousEventPartDetails.getM_strPartNumber(),
									previousEventPartDetails.getM_strPartName(),
									BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_DROPPED.value),
									BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
									);
							enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
							enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
							
							//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
							if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
								m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
							}
							enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
							for(EnterACCSuppSummaryACCDataDetailsDTO rawACCData : m_lenterACCSuppSummaryACCDataDetailsDTOList){
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Check the total number of ACC for an Part Record and all MTO Combination order by Effective date of these ACCs.
									List<Map<String,Object>> allACCs = accProcessingBatchDAO.fetchAllACCForPartDataAndAllMTOS(enterACCApplicationsSuppMTOSummaryDVO
											,enterACCSuppSummaryPartLevelDataDTO, previousEventPartDetails, previousEventPartDetails, "PART_DROPPED", "BASE" );
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - Effective Date and Rule ID.
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("RULE_ID"),
												((String)accData.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accData.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"",
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												Utility.convertFromUtilDateToStr((Date)accData.get("EFFECTIVE_DATE"),"MM/dd/yyyy"),
												String.valueOf((Integer)accData.get("ACC_STATUS")),
												"",//Utility.convertSqlTimestamptoStringACC((Timestamp)accData.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"),
												(String)accData.get("RULE_DESC_TEXT")!=null ? (String)accData.get("RULE_DESC_TEXT") : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									
									for(Map<String,Object> accData : allACCs){
										//ACC Cost Data - ACC, Comments and Status
										enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
												(String)accData.get("APP_COST_CHANGE_CODE"),(String)accData.get("APP_COST_CHANGE_CODE"),
												false,
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
												new EnterACCSuppSummaryACCCommentsDTO(
														(String)accData.get("ACC_COMMENTS"), 
														(String)accData.get("CODE_DESC_TEXT")!=null ? ((String)accData.get("CODE_DESC_TEXT")).split("@_@")[0] :"", 
																(String)accData.get("CODE_DESC_TEXT")!=null && ((String)accData.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
												    					((String)accData.get("CODE_DESC_TEXT")).split("@_@")[1] :""),
												String.valueOf((Integer)accData.get("ACC_STATUS")) ,
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "",
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null &&
												!(((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S")) ? (String)accData.get("IS_BASE_OR_CURRENT_EVENT") : "B",
												(String)accData.get("IS_BASE_OR_CURRENT_EVENT")!=null 
													&& (((String)accData.get("IS_BASE_OR_CURRENT_EVENT")).equalsIgnoreCase("S"))
													? BatchConstantsIF.ACC_APP_CONSTANTS.ACC_CHANGED_FROM_S_TO_CB : "");
										enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									}
									
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
									
									//List of ACC Data - this code block to define the no. of ACC present so that further in code we can set ACC object at appropriate location.
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								}
								
								//Creating Dummy ACC Cost records based on the total number of ACC present in Part MTO combination.
								if(enterACCSuppSummaryACCCostDataDTOList.isEmpty()){
									EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
									for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
										accCostData = new EnterACCSuppSummaryACCCostDataDTO();
										accCostData.setM_decACCCost(new BigDecimal(0.0000));
										accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
										accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
										accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
										enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
									}
								}
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//ACC applied then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								//used to get location - fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData);
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_decACCAmount(),
										rawACCData.getM_strAppCostChangeCode(),
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(
												rawACCData.getM_strAccComments(), 
												rawACCData.getM_strAccCommentDesc(), 
												rawACCData.getM_strAccCommentNote()),
										rawACCData.getM_strAccStatus(),
										rawACCData.getM_strRuleId()!=null ? rawACCData.getM_strRuleId() : "",
										rawACCData.getM_strAccRulePartCharMatch(),
										rawACCData.getM_strEffectiveDate(),
										rawACCData.getM_strModifiedBy(),
										rawACCData.getM_strModifiedDate(),
										rawACCData.getM_strBaseOrCurrentEvent());
								m_decTotalACC = m_decTotalACC.add(rawACCData.getM_decACCAmount());
								if(rawACCData.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value)){
									m_decTotalACCAppliedByBatch = m_decTotalACCAppliedByBatch.add(rawACCData.getM_decACCAmount());
								}
								enterACCSuppSummaryACCCostDataDTOList.set(
										fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO), rawACCData),
										enterACCSuppSummaryACCCostDataDTO);
							}
							
							//Check if the Total ACC cost fetched clears the balance else have one more ACC row added.
							if(!(m_decTotalACC.compareTo(findVariance(
									previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
									previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
									previousEventPartDetails.getM_decShareRatePercent())) == 0)){
								
								//Check if the hashmap m_hmpACCDisplayLabelEffDateDTO has the part record and respective to the part we have an 
								//No ACC applied status then add the below ACC data at the same position in the list enterACCSuppSummaryACCCostDataDTOList.
								
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										(findVariance(
												previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										(findVariance( previousEventPartDetails.getM_decEndCostAmount(),
												new BigDecimal(0.0000), 
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()))
											.subtract(m_decTotalACC),
										//"",
										strRuleACC==null ? "" : strRuleACC[0],
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
										//"",
										//"",
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ? "" : strRuleACC[2],//Changed Assign ACC by Rule
										strRuleACC==null ? "" : strRuleACC[1],//Changed Assign ACC by Rule
										m_strDefaultEffectiveDate,
										"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
								
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(//"",
												//"",
												strRuleACC==null ?  "": strRuleACC[2],//TODO Changed Assign ACC by Rule
												strRuleACC==null ? "" : strRuleACC[1],
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,"",""));
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
												"",
												strRuleACC==null ? "" : strRuleACC[3]));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(//"",
													//"",
													strRuleACC==null ? "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ? "" : strRuleACC[0],
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
										}
									}
									
								}
								//Checks if already we have a NO_ACC record in the enterACCSuppSummaryACCCostDataDTOList then set the DTO at that location else adds. 
								if(location<enterACCSuppSummaryACCCostDataDTOList.size()){
									enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								} else {
									enterACCSuppSummaryACCCostDataDTOList.add(location, enterACCSuppSummaryACCCostDataDTO);
								}
							}
							BigDecimal balanceCost = ((findVariance(
									previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
									previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
									previousEventPartDetails.getM_decShareRatePercent())).subtract(m_decTotalACC)).add(m_decTotalACCAppliedByBatch);
							//Display data on screen based on the what user has selected in the DataToDisplay field. 
							/*if((balanceCost.compareTo(BigDecimal.ZERO) == 0
									&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									|| (!(balanceCost.compareTo(BigDecimal.ZERO) == 0) 
											&& StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES))
									|| StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.BOTH_RESOLVED_UNRESOLVED_BALANCES)
								){*/
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										findEndCost(previousEventPartDetails.getM_decEndCostAmount(), previousEventPartDetails.getM_intPartQty(),
												previousEventPartDetails.getM_decShareRatePercent(), previousEventPartDetails.getM_decMCCAmount()),
										new BigDecimal(0.0000),
										new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
												previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
												, previousEventPartDetails.getM_decMCCAmount())),
										new BigDecimal(0.0000),
										enterACCSuppSummaryACCCostDataDTOList,
										balanceCost,
										femdDTO
										);
								
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							/*} else {
								//As we have no key present in m_hmpEnterACCSuppSummaryACCDataDTO hash map need to clear the key-value from m_hmpACCDisplayLabelEffDateDTO and also from the m_lEnterACCSuppSummaryPartLevelDataDTOList list.
								if(!m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									m_hmpACCDisplayLabelEffDateDTO.remove(enterACCSuppSummaryPartLevelDataDTO);
									m_lEnterACCSuppSummaryPartLevelDataDTOList.remove(enterACCSuppSummaryPartLevelDataDTO);
								}
							}*/
							
						} else{
							//If ACC do not exist check if variance exists only if variance exist add the Data and mark the ACC as pending ACC
							//Check if variance exist
							if(/*!(StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), BatchConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES))
									&&*/ !((findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000), 
											previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
									previousEventPartDetails.getM_decShareRatePercent()))
									.compareTo(BigDecimal.ZERO) == 0)){
								log.info("No Approved acc found in PartAddedDropped base");
								//Main Part Details Data Object
								enterACCSuppSummaryPartLevelDataDTO = new EnterACCSuppSummaryPartLevelDataDTO(
										previousEventPartDetails.getM_strProcSectCode(),
										previousEventPartDetails.getM_strSupplierNumber(),
										previousEventPartDetails.getM_strSupplierName(),
										previousEventPartDetails.getM_strPlantLocCode(),
										previousEventPartDetails.getM_strPartSectionCode(),
										previousEventPartDetails.getM_strModelCatCode(),
										previousEventPartDetails.getM_decShareRatePercent(),
										previousEventPartDetails.getM_intPartQty(),
										previousEventPartDetails.getM_strPartColorCode(),
										previousEventPartDetails.getM_strPartNumber(),
										previousEventPartDetails.getM_strPartName(),
										BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON_DB_TO_SCREEN_MAP.get(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_DISTINGUISHING_REASON.PART_DROPPED.value),
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE
										);
								enterACCSuppSummaryPartLevelDataDTO.setM_strSupplierNumberBaseCurrent(previousEventPartDetails.getM_strSupplierNumber());
								enterACCSuppSummaryPartLevelDataDTO.setM_strPartNumberBaseCurrent(previousEventPartDetails.getM_strPartNumber());
								
								//Check the acc seq and arrange the ACC fetched accordingly.
								if(!m_hmpACCDisplayLabelEffDateDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									
									//Adding the ACC Cost Label in the object which are displayed on screen before the Cost data 
									//which includes the ACC drop down 
									//and left of this we display Effective date and rule id so creating one more object for the same.
									m_lEnterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									
									//List of ACCs seq - Effective Date and Rule ID.
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											//"",
											//"",
											strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
											strRuleACC==null ?  "" : strRuleACC[1],
											m_strDefaultEffectiveDate,
											m_strDefaultEffectiveDate,
											//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,"","");
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
													: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											"",
											strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
											);
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Effective date and rule id
											"", "", "", "", "", enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									//List of ACCs seq - ACC, Comments and Status
									enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
											//"","",
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
											strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
											false,
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											new EnterACCSuppSummaryACCCommentsDTO(
													"", 
													"", 
													""),
											//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
											strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
											BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,"");
									
									enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
									enterACCSuppSummaryACCCostDataDTOList.add(enterACCSuppSummaryACCCostDataDTO);
									enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(//Labels
											"Previous",
											"Current",
											"Difference",
											"MCC",
											"Balance",
											enterACCSuppSummaryACCCostDataDTOList
											);
									m_lEnterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									
									m_hmpACCDisplayLabelEffDateDTO.put(enterACCSuppSummaryPartLevelDataDTO,m_lEnterACCSuppSummaryACCDataDTO);
								}
								
								//Also add the same EnterACCSuppSummaryPartLevelDataDTO DTO in the HashMap as a key and value as EnterACCSuppSummaryACCDataDTO
								int location = fetchLocationToAddACCInList(m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO));
								
								if(location > m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1){
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0)
										.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
												new EnterACCSuppSummaryACCCostDataDTO(//"",
												//"",
												strRuleACC==null ?  "" : strRuleACC[2], //TODO Changed Assign ACC by Rule
												strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
												m_strDefaultEffectiveDate,
												m_strDefaultEffectiveDate,
												//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,"",""));
												strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
														: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
												"",
												strRuleACC==null ?  "" : strRuleACC[3] //TODO Changed Assign ACC by Rule
											));
									
									m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(1)
									.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
											new EnterACCSuppSummaryACCCostDataDTO(//"",
													//"",
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule
													false,
													new EnterACCSuppSummaryACCCommentsDTO(),
													new EnterACCSuppSummaryACCCommentsDTO(),
													//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
													strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
															: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,
													BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE,""));
									
									//Need to add the NO_ACC record in the previous MTOs objects list if current is not the first MTOs being iterated.
									if(null != m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO)
											 && m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size()>0){
										for(int mtoNo = 0; mtoNo < m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).size(); mtoNo++){
											m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(mtoNo)
											.getM_lenterACCSuppSummaryACCCostDataDTOList().add(location,
													new EnterACCSuppSummaryACCCostDataDTO(
															new BigDecimal(0.0000),
															new BigDecimal(0.0000),
															"",
															false,
															false,
															new EnterACCSuppSummaryACCCommentsDTO(),
															BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value,
															"",
															"",
															m_strDefaultEffectiveDate,
															"","", BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE));
										}
									}
									
								}
								
								//ACC Cost Data
								enterACCSuppSummaryACCCostDataDTO = new EnterACCSuppSummaryACCCostDataDTO(
										findVariance( previousEventPartDetails.getM_decEndCostAmount(),new BigDecimal(0.0000), 
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()),
										findVariance( previousEventPartDetails.getM_decEndCostAmount(),new BigDecimal(0.0000), 
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()),
										//"",
										strRuleACC==null ?  "" : strRuleACC[0],//TODO Changed Assign ACC by Rule,
										false,
										false,
										new EnterACCSuppSummaryACCCommentsDTO(),
										//BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value,
										//"",
										//"",
										strRuleACC==null ?  BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value //TODO Changed Assign ACC by Rule
												: BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value,//ACC assignment based on Rules
										strRuleACC==null ?  "" : strRuleACC[2],//TODO Changed Assign ACC by Rule
										strRuleACC==null ?  "" : strRuleACC[1],//TODO Changed Assign ACC by Rule
										m_strDefaultEffectiveDate,"","",
										BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
								
								//List of ACC Data
								enterACCSuppSummaryACCCostDataDTOList = new ArrayList<EnterACCSuppSummaryACCCostDataDTO>();
								EnterACCSuppSummaryACCCostDataDTO accCostData = new EnterACCSuppSummaryACCCostDataDTO();
								//for(int i = 0; i<m_lenterACCSuppSummaryACCDataDetailsDTOList.size(); i++){
								for(int i = 0; i<m_hmpACCDisplayLabelEffDateDTO.get(enterACCSuppSummaryPartLevelDataDTO).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
									accCostData = new EnterACCSuppSummaryACCCostDataDTO();
									accCostData.setM_decACCCost(new BigDecimal(0.0000));
									accCostData.setM_decOriginalACCCost(new BigDecimal(0.0000));
									accCostData.setM_strAccStatus(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value);
									accCostData.setM_strBaseOrCurrentEvent(BatchConstantsIF.ACC_APP_CONSTANTS.IS_CURRENT_BASE_EVENT_BASE);
									enterACCSuppSummaryACCCostDataDTOList.add(i, accCostData);
								}
								enterACCSuppSummaryACCCostDataDTOList.set(location, enterACCSuppSummaryACCCostDataDTO);
								
								//Complete Cost Data object consisting Previous, Current ACC MCC Balance.
								//Adding the ACC Cost in the object
								enterACCSuppSummaryACCDataDTO = new EnterACCSuppSummaryACCDataDTO(
										findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
												previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
												, previousEventPartDetails.getM_decMCCAmount()),
										new BigDecimal(0.0000),
										new BigDecimal(0.0000).subtract(findEndCost(previousEventPartDetails.getM_decEndCostAmount(), 
												previousEventPartDetails.getM_intPartQty(), previousEventPartDetails.getM_decShareRatePercent()
												, previousEventPartDetails.getM_decMCCAmount())),
										new BigDecimal(0.0000),
										enterACCSuppSummaryACCCostDataDTOList,
										findVariance(previousEventPartDetails.getM_decEndCostAmount(), new BigDecimal(0.0000),  
												previousEventPartDetails.getM_decMCCAmount(), new BigDecimal(0.0000), previousEventPartDetails.getM_intPartQty(), 
												previousEventPartDetails.getM_decShareRatePercent()),
										femdDTO
										);
								
								//Main Part Details Data Object's list - Where adding the Part Details Object only if it does not exists.
								if(!m_lEnterACCSuppSummaryPartLevelDataDTOList.contains(enterACCSuppSummaryPartLevelDataDTO)){
									m_lEnterACCSuppSummaryPartLevelDataDTOList.add(enterACCSuppSummaryPartLevelDataDTO);
								}
								if(m_hmpEnterACCSuppSummaryACCDataDTO.containsKey(enterACCSuppSummaryPartLevelDataDTO)){
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_hmpEnterACCSuppSummaryACCDataDTO.get(enterACCSuppSummaryPartLevelDataDTO).add(enterACCSuppSummaryACCDataDTO);
								} else {
									//Adding the Part Details Data Object as the key and the respective Complete Cost Data object in it's list.
									m_lenterACCSuppSummaryACCDataDTO = new ArrayList<EnterACCSuppSummaryACCDataDTO>();
									m_lenterACCSuppSummaryACCDataDTO.add(enterACCSuppSummaryACCDataDTO);
									m_hmpEnterACCSuppSummaryACCDataDTO.put(enterACCSuppSummaryPartLevelDataDTO, m_lenterACCSuppSummaryACCDataDTO);
								}
							}
						}
							
					}
				}
			}
			
		
		log.info("\n Exiting method - compareCurrentAndPreviousEventForAddedDroppedParts() in "+CLASS_NAME);
	}
	
	/**
	 * This method is used to get the location at which ACC dto shall be placed.
	 * @param accDataList
	 * @param currentACC
	 * @return
	 */
	private int fetchLocationToAddACCInList(ArrayList<EnterACCSuppSummaryACCDataDTO> accDataList, EnterACCSuppSummaryACCDataDetailsDTO currentACC){
		
		EnterACCSuppSummaryACCDataDTO toCompareDTOEffDate = accDataList.get(0);
		EnterACCSuppSummaryACCDataDTO toCompareDTOLabel = accDataList.get(1);
		int location = 0;
		for(int i=0; i<toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
			if(StringUtils.equals(toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strACC(), currentACC.getM_strAppCostChangeCode())
					&& StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strAccStatus(), currentACC.getM_strAccStatus())
					&& StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strRuleId(), currentACC.getM_strRuleId())
					&& StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strAccRulePartCharMatch(), currentACC.getM_strAccRulePartCharMatch())
					&& StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strEffectiveDate(), currentACC.getM_strEffectiveDate())
					&& StringUtils.equals(toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getEnterACCSuppSummaryACCCommentsDTO().getM_strACCCommentCode(), currentACC.getM_strAccComments())
					//&& StringUtils.equalsIgnoreCase(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strModifiedDate(), currentACC.getM_strModifiedDate())
					&& StringUtils.equals(toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strOriginalBaseOrCurrentEvent(), currentACC.getM_strBaseOrCurrentEvent())
					){
				location=i;
				break;
			}
		}
		return location;
	}
	
	/**
	 * This method is used to check the location of the 'NO_ACC' record.
	 * @param accDataList
	 * @return
	 */
	private int fetchLocationToAddACCInList(ArrayList<EnterACCSuppSummaryACCDataDTO> accDataList){
		
		EnterACCSuppSummaryACCDataDTO toCompareDTOEffDate = accDataList.get(0);
		EnterACCSuppSummaryACCDataDTO toCompareDTOLabel = accDataList.get(1);
		int location = 0;
		for(int i=0; i<toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().size(); i++){
			if((StringUtils.equals(toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strACC(), "" ) &&
					StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strAccStatus(), BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.NO_ACC.value))
					|| (!StringUtils.equals(toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strACC(), "" ) && 
							StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strAccStatus(), BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value))
					&& StringUtils.equals(toCompareDTOEffDate.getM_lenterACCSuppSummaryACCCostDataDTOList().get(i).getM_strEffectiveDate(), m_strDefaultEffectiveDate)
					){
				location=i;
				break;
			} else if(i==toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().size()-1) {
				location=toCompareDTOLabel.getM_lenterACCSuppSummaryACCCostDataDTOList().size();
			}
		}
		return location;
	}
	
	/**
	 * This method is used to compare current and previous Event Part Data and decide the match type.
	 * @param currentEventPartDetails
	 * @param previousEventPartDetails
	 * @param typeOfMatch
	 * @return
	 * @throws Exception
	 */
	private boolean compareCurrentAndPreviousPartData(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO,
			EnterACCEventPartDetailsDTO currentEventPartDetails, 
			EnterACCEventPartDetailsDTO previousEventPartDetails, String typeOfMatch) {
		
		boolean recordMatched = false;
		/*
		 * Check if FEMD in Current(currentEventPartDetails) and Base(previousEventPartDetails) event part record or  matched with list 'enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()'
		 */
		boolean recordFound=false; 
		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO:enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
				if(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null&&enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel()!=null&&
						enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType()!=null&&
						enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel()!=null&&
						
						enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeFrame())&&
						enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeFrame())&&
						enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeEngine())&&
						enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeEngine())&&
						enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeMission())&&
						enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeMission())&&
						enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeDifferential())&&
						enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeDifferential())&&
						enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetails.getM_strTgtModelDevCodeFrame())&&
						enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType().equalsIgnoreCase(previousEventPartDetails.getM_strMTCTypeFrame())&&
						enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetails.getM_strTgtModelDevCodeEngine())&&
						enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType().equalsIgnoreCase(previousEventPartDetails.getM_strMTCTypeEngine())&&
						enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetails.getM_strTgtModelDevCodeMission())&&
						enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType().equalsIgnoreCase(previousEventPartDetails.getM_strMTCTypeMission())&&
						enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetails.getM_strTgtModelDevCodeDifferential())&&
						enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType().equalsIgnoreCase(previousEventPartDetails.getM_strMTCTypeDifferential())&&
						previousEventPartDetails.getM_strModelCatCode().trim().equalsIgnoreCase(currentEventPartDetails.getM_strModelCatCode().trim())){
					recordFound=true;
					break;
				}
		}
		
		if(recordFound){
		
			if("EXACT_MATCH".equals(typeOfMatch)){
				recordMatched = 
					currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode())
					&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent())
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) == 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode());
						
			} else if("SUPP_CHANGE_MATCH".equals(typeOfMatch)){
				recordMatched = 
					currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode())
					&& !(currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber()))
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent())
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) == 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode());
				
			} else if("PROC_GROUP_CHANGE_MATCH".equals(typeOfMatch)){
				recordMatched = 
					!(currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode()))
					&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent())
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) == 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode());
				
			} else if("SHARE_RATE_CHANGE_MATCH".equals(typeOfMatch)){
				recordMatched = 
					currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode())
					&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& !(currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent()))
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) == 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode());
				
			} else if("PART_QTY_CHANGE_MATCH".equals(typeOfMatch)){
				recordMatched = 
					currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode())
					&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent())
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) != 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode());
				
			} else if("DESIGN_SECT_CHANGE_MATCH".equals(typeOfMatch)){
				recordMatched = 
					currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode())
					&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& !(currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode()))
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent())
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) == 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode());
				
			}else if("PART_COLOR_CODE_CHANGE_MATCH".equals(typeOfMatch)){
				recordMatched = 
					currentEventPartDetails.getM_strProcSectCode().equalsIgnoreCase(previousEventPartDetails.getM_strProcSectCode())
					&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(previousEventPartDetails.getM_strSupplierNumber())
					&& currentEventPartDetails.getM_strPlantLocCode().equalsIgnoreCase(previousEventPartDetails.getM_strPlantLocCode())
					&& currentEventPartDetails.getM_strPartSectionCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartSectionCode())
					&& currentEventPartDetails.getM_strModelCatCode().equals(previousEventPartDetails.getM_strModelCatCode())
					&& currentEventPartDetails.getM_decShareRatePercent().equals(previousEventPartDetails.getM_decShareRatePercent())
					&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetails.getM_intPartQty()) == 0
					&& currentEventPartDetails.getM_strPartNumber().equalsIgnoreCase(previousEventPartDetails.getM_strPartNumber())
					&& !(currentEventPartDetails.getM_strPartColorCode().equalsIgnoreCase(previousEventPartDetails.getM_strPartColorCode()));
				
			}
		}
		return recordMatched;
	}
	
	/**
	 * This method is used to compare current and previous Event Part Data and decide the match type.
	 * @param currentEventPartDetails
	 * @param previousEventPartDetails
	 * @param typeOfMatch
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	private String compareCurrentAndPreviousPartDataMultipleHierarchy(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO,
			EnterACCEventPartDetailsDTO currentEventPartDetails, EnterACCEventPartDetailsDTO previousEventPartDetails, 
			ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO) {
		
		String strMultipleIndicatorChangeIdentifier=null;
		
		/*
		 * Check if FEMD in Current(currentEventPartDetails) and Base(previousEventPartDetails) event part record or  matched with list 'enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()'
		 */
		ArrayList<Integer> prevPartIndexInList = new ArrayList<Integer>();
		int prevPartIndex=0;
		for(EnterACCEventPartDetailsDTO previousEventPartDetailsObj : m_lEnterACCPreviousEventPartDetailsDTO){
			if(!previousEventPartDetailsObj.isM_bolMatchDone() 
					&& previousEventPartDetailsObj.getM_strPartNumber().trim().equalsIgnoreCase(currentEventPartDetails.getM_strPartNumber().trim())
					//TODO Remove this condition once plant is added as achange indicator
					&& previousEventPartDetailsObj.getM_strPlantLocCode().trim().equalsIgnoreCase(currentEventPartDetails.getM_strPlantLocCode().trim())){
				for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO:enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
						if(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null&&enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel()!=null&&
								enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType()!=null&&
								enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel()!=null&&
								
								enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeFrame())&&
								enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeFrame())&&
								enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeEngine())&&
								enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeEngine())&&
								enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeMission())&&
								enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeMission())&&
								enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel().equalsIgnoreCase(currentEventPartDetails.getM_strTgtModelDevCodeDifferential())&&
								enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType().equalsIgnoreCase(currentEventPartDetails.getM_strMTCTypeDifferential())&&
								enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetailsObj.getM_strTgtModelDevCodeFrame())&&
								enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType().equalsIgnoreCase(previousEventPartDetailsObj.getM_strMTCTypeFrame())&&
								enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetailsObj.getM_strTgtModelDevCodeEngine())&&
								enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType().equalsIgnoreCase(previousEventPartDetailsObj.getM_strMTCTypeEngine())&&
								enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetailsObj.getM_strTgtModelDevCodeMission())&&
								enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType().equalsIgnoreCase(previousEventPartDetailsObj.getM_strMTCTypeMission())&&
								enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel().equalsIgnoreCase(previousEventPartDetailsObj.getM_strTgtModelDevCodeDifferential())&&
								enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType().equalsIgnoreCase(previousEventPartDetailsObj.getM_strMTCTypeDifferential())&&
								previousEventPartDetailsObj.getM_strModelCatCode().trim().equalsIgnoreCase(currentEventPartDetails.getM_strModelCatCode().trim())){
							prevPartIndexInList.add(prevPartIndex);
							break;
						}
					
				}
			}
			prevPartIndex++;
		}
		
		//Hierarchy is 1-Proc Group, 2-Supplier Number, 3-Qty, 4-Share Rate, 5-Design Section and 6-Plant
		HashMap hmapHierarchyPartObj = new HashMap<String, EnterACCEventPartDetailsDTO>();
		String hierarchyChanges = "";
		EnterACCEventPartDetailsDTO previousEventPartDetailsIndexObj;
		EnterACCEventPartDetailsDTO previousEventPartDetailsIndexFinalObj;
		for(Integer index : prevPartIndexInList){
			hierarchyChanges  = "";
			previousEventPartDetailsIndexObj = new EnterACCEventPartDetailsDTO();
			previousEventPartDetailsIndexObj = m_lEnterACCPreviousEventPartDetailsDTO.get(index);
			
			if(!previousEventPartDetailsIndexObj.getM_strProcSectCode().equalsIgnoreCase(currentEventPartDetails.getM_strProcSectCode())){
				hierarchyChanges = hierarchyChanges + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value();
			}
			
			if(!previousEventPartDetailsIndexObj.getM_strSupplierNumber().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber())){
				hierarchyChanges = hierarchyChanges + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value();
			}
			
			if(!(previousEventPartDetailsIndexObj.getM_intPartQty()==currentEventPartDetails.getM_intPartQty())){
				hierarchyChanges = hierarchyChanges + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value();
			}
			
			if(!(previousEventPartDetailsIndexObj.getM_decShareRatePercent().compareTo(currentEventPartDetails.getM_decShareRatePercent()) == 0)){
				hierarchyChanges = hierarchyChanges + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SHARE_RATE_CHANGE.value();
			}
			
			if(!previousEventPartDetailsIndexObj.getM_strPartSectionCode().equalsIgnoreCase(currentEventPartDetails.getM_strPartSectionCode())){
				hierarchyChanges = hierarchyChanges + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value();
			}
			
			if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim().contains("PSP")){
				if(!previousEventPartDetailsIndexObj.getM_strPartColorCode().equalsIgnoreCase(currentEventPartDetails.getM_strPartColorCode())){
					hierarchyChanges = hierarchyChanges + BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value();
				}
			}
			
			//TODO - For future changes when Plant Change is added and is considered in hierarchy
			/*if(previousEventPartDetailsIndexObj.getM_strPlantLocCode().equalsIgnoreCase(currentEventPartDetails.getM_strPlantLocCode())){
				
			}*/
			if(!hierarchyChanges.isEmpty()){
				previousEventPartDetailsIndexObj.setM_intIndexForHierarchy(index);
				hmapHierarchyPartObj.put(hierarchyChanges, previousEventPartDetailsIndexObj);
			}
			
			
		}
		if(!hmapHierarchyPartObj.isEmpty()){
			Set<String> keys = hmapHierarchyPartObj.keySet();
			strMultipleIndicatorChangeIdentifier="";
	        for(String key: keys){
	            System.out.println(key);
	            if(!strMultipleIndicatorChangeIdentifier.isEmpty() && strMultipleIndicatorChangeIdentifier.length()>key.length()){
	            	strMultipleIndicatorChangeIdentifier = key;
	            } else {
	            	strMultipleIndicatorChangeIdentifier = key;
	            }
	        }
	        
	        previousEventPartDetailsIndexFinalObj = new EnterACCEventPartDetailsDTO();
	        previousEventPartDetailsIndexFinalObj = (EnterACCEventPartDetailsDTO) hmapHierarchyPartObj.get(strMultipleIndicatorChangeIdentifier);
	        previousEventPartDetailsIndexFinalObj.setM_bolMatchDone(true);
			setPreviousEventPartDetailsData(previousEventPartDetails,previousEventPartDetailsIndexFinalObj);
			//Setting this variable as true in order to mark the prev obj as processed
			m_lEnterACCPreviousEventPartDetailsDTO.get(
						((EnterACCEventPartDetailsDTO) hmapHierarchyPartObj.get(strMultipleIndicatorChangeIdentifier)).getM_intIndexForHierarchy()
					).setM_bolMatchDone(true);
		}
		
		return strMultipleIndicatorChangeIdentifier;
	}
	
	/**
	 * This method is used to find variance.
	 * @param previousCost
	 * @param currentCost
	 * @param mccCost
	 * @param partQty
	 * @param sharePercent
	 * @return
	 */
	private BigDecimal findVariance(BigDecimal previousCost, BigDecimal currentCost, BigDecimal previousMccCost, BigDecimal currentMccCost, 
			int partQty, BigDecimal sharePercent){
		
		BigDecimal variance=new BigDecimal(0.0000);
		BigDecimal previousTotalCost = previousCost.compareTo(BigDecimal.ZERO)==0 && previousMccCost!=null && previousMccCost.compareTo(BigDecimal.ZERO)==0 ? new BigDecimal(0.0000) :
											findEndCost(previousCost, partQty, sharePercent, previousMccCost);
		
		BigDecimal currentTotalCost = currentCost.compareTo(BigDecimal.ZERO)==0  && currentMccCost!=null && currentMccCost.compareTo(BigDecimal.ZERO)==0 ? new BigDecimal(0.0000) :
											findEndCost(currentCost, partQty, sharePercent, currentMccCost);
		
		/*return previousCost.compareTo(BigDecimal.ZERO)==0 || currentCost.compareTo(BigDecimal.ZERO)==0 ? currentTotalCost.subtract(previousTotalCost)
						: (currentTotalCost.subtract(previousTotalCost)).subtract(findMCCCost(currentMccCost, 
								partQty, sharePercent));*/
		//When we have current 0 means the record formation taking place is for base/previous event record hence not subtracting MCC as only current MCC is subtracted. 
		
		variance=(currentCost.compareTo(BigDecimal.ZERO)==0 ? currentTotalCost.subtract(previousTotalCost)
				: (currentTotalCost.subtract(previousTotalCost)).subtract(findMCCCost(currentMccCost, 
						partQty, sharePercent))).setScale(4, RoundingMode.DOWN);
		
		//0.0001 or -0.0001 is coming due to 4 decimal calculation which is negligible.  
		if(variance.toString().trim().equalsIgnoreCase("0.0001") || variance.toString().trim().equalsIgnoreCase("-0.0001"))
			variance=new BigDecimal(0.0000);
		
		return variance;
	}
	
	/**
	 * This method is used to find the MCC.
	 * @param mccCost
	 * @param partQty
	 * @param sharePercent
	 * @return
	 */
	private BigDecimal findMCCCost(BigDecimal mccCost, int partQty, BigDecimal sharePercent){
		return ((mccCost.multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)))).setScale(4, RoundingMode.DOWN);
	}
	
	/**
	 * This method is used to find the End cost.
	 * @param endCost
	 * @param partQty
	 * @param sharePercent
	 * @param mccCost
	 * @return
	 */
	private BigDecimal findEndCost(BigDecimal endCost, int partQty, BigDecimal sharePercent, BigDecimal mccCost){
		
		/*BigDecimal partTotalCost = (endCost.multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)));
		
		BigDecimal mccTotalCost = findMCCCost(mccCost, partQty, sharePercent);*/
			//(mccCost.multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)));
		//Above code is commented as it is calculating BC and MCC separately and then adding their result. Instead we can add BC and MCC at first and then calculate the ened cost.
		BigDecimal partTotalCost = ((endCost.add(mccCost)).multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)));
		
		//return (partTotalCost.add(mccTotalCost)).setScale(4, RoundingMode.DOWN);
		return (partTotalCost).setScale(4, RoundingMode.DOWN);
	}
	
	private String[] assignACCBasedOnRules(AccRuleEnum value, EnterACCEventPartDetailsDTO previousEventPartDetails, 
			EnterACCEventPartDetailsDTO currentEventPartDetails, ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCPreviousEventPartDetailsDTO){
		String [] args = null;
		
		//Check if the rule is enabled
		if(!value.isEnabled())
			return args;
		
		int[] charMatchArray;
		
		if(value.compareTo(AccRuleEnum.EXPN)==0){
			//TODO - Logical check for this rule
			if(null!=m_lEnterACCPreviousEventPartDetailsDTO && !m_lEnterACCPreviousEventPartDetailsDTO.isEmpty()){
				charMatchArray = new int[]{11,10,8,5};
				for(int charMatch : charMatchArray){
					for(EnterACCEventPartDetailsDTO previousEventPartDetailsObj : m_lEnterACCPreviousEventPartDetailsDTO){
						if(!previousEventPartDetailsObj.isM_bolMatchDone() 
								//Added here this condition because if the part is a exact match then it will be considered by rules in the else block (if m_lEnterACCPreviousEventPartDetailsDTO is null)
								&& !StringUtils.equals(currentEventPartDetails.getM_strPartNumber().trim(), previousEventPartDetailsObj.getM_strPartNumber().trim())&& currentEventPartDetails.getM_strPartNumber().trim().length()>=charMatch 
								&& previousEventPartDetailsObj.getM_strPartNumber().trim().length()>=charMatch &&
								currentEventPartDetails.getM_strPartNumber().substring(0, charMatch).matches(previousEventPartDetailsObj.getM_strPartNumber().substring(0, charMatch))
								&& (previousEventPartDetailsObj.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
										&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))
										|| (!previousEventPartDetailsObj.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
												&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))
												//&& StringUtils.equals(previousEventPartDetailsObj.getM_strProcSectCode(),currentEventPartDetails.getM_strProcSectCode())
												&& StringUtils.equals(previousEventPartDetailsObj.getM_strPlantLocCode(),currentEventPartDetails.getM_strPlantLocCode())
												&& StringUtils.equals(previousEventPartDetailsObj.getM_strModelCatCode(),currentEventPartDetails.getM_strModelCatCode())
												//&& StringUtils.equals(previousEventPartDetailsObj.getM_strPartSectionCode(),currentEventPartDetails.getM_strPartSectionCode())
												){
							args = new String[4];
							args[0] = AccRuleEnum.EXPN.getAppCostChangeCode();
							args[1] = String.valueOf(charMatch);
							args[2] = AccRuleEnum.EXPN.getRuleId();
							args[3] = AccRuleEnum.EXPN.getRuleDescText();
							previousEventPartDetailsObj.setM_bolMatchDone(true);
							setPreviousEventPartDetailsData(previousEventPartDetails,previousEventPartDetailsObj);
							return args;
						}
					}
				}
			}else{
				if((previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
						&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))
						|| (!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
								&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))){
					args = new String[4];
					args[0] = AccRuleEnum.EXPN.getAppCostChangeCode();
					args[1] = String.valueOf(previousEventPartDetails.getM_strPartNumber().length());
					args[2] = AccRuleEnum.EXPN.getRuleId();
					args[3] = AccRuleEnum.EXPN.getRuleDescText();
					return args;
				}
			}
		} else if(value.compareTo(AccRuleEnum.FSTN)==0){
			if(previousEventPartDetails.getM_strPartNumber().charAt(0)=='9' && currentEventPartDetails.getM_strPartNumber().charAt(0)=='9'){
				args = new String[4];
				args[0] = AccRuleEnum.FSTN.getAppCostChangeCode();
				args[1] = String.valueOf(previousEventPartDetails.getM_strPartNumber().length());
				args[2] = AccRuleEnum.FSTN.getRuleId();
				args[3] = AccRuleEnum.FSTN.getRuleDescText();
				return args;
			}
		} else if(value.compareTo(AccRuleEnum.NEXP)==0){
			//TODO - Logical check for this rule
			if(null!=m_lEnterACCPreviousEventPartDetailsDTO && !m_lEnterACCPreviousEventPartDetailsDTO.isEmpty()){
				charMatchArray = new int[]{13};//Rule to be applied for 13 char match
				for(int charMatch : charMatchArray){
					for(EnterACCEventPartDetailsDTO previousEventPartDetailsObj : m_lEnterACCPreviousEventPartDetailsDTO){
						if(!previousEventPartDetailsObj.isM_bolMatchDone()
								//Added here this condition because if the part is a exact match then it will be considered by rules in the else block (if m_lEnterACCPreviousEventPartDetailsDTO is null)
								&& !StringUtils.equals(currentEventPartDetails.getM_strPartNumber().trim(), previousEventPartDetailsObj.getM_strPartNumber().trim())
								&& currentEventPartDetails.getM_strPartNumber().trim().length()>=charMatch
								&& previousEventPartDetailsObj.getM_strPartNumber().trim().length()>=charMatch
								&& currentEventPartDetails.getM_strPartNumber().substring(0, charMatch)
									.matches(previousEventPartDetailsObj.getM_strPartNumber().substring(0, charMatch))
								&& (!previousEventPartDetailsObj.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
										&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999")
										&& !m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
		            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber())  
										&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
		            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())
										&& !(previousEventPartDetailsObj.getM_strSupplierNumber().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber())))
								//&& StringUtils.equals(previousEventPartDetailsObj.getM_strProcSectCode(),currentEventPartDetails.getM_strProcSectCode())
								&& StringUtils.equals(previousEventPartDetailsObj.getM_strPlantLocCode(),currentEventPartDetails.getM_strPlantLocCode())
								&& StringUtils.equals(previousEventPartDetailsObj.getM_strModelCatCode(),currentEventPartDetails.getM_strModelCatCode())
								&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetailsObj.getM_intPartQty()) == 0
								//&& StringUtils.equals(previousEventPartDetailsObj.getM_strPartSectionCode(),currentEventPartDetails.getM_strPartSectionCode())
								){
							args = new String[4];
							args[0] = AccRuleEnum.NEXP.getAppCostChangeCode();
							args[1] = String.valueOf(charMatch);
							args[2] = AccRuleEnum.NEXP.getRuleId();
							args[3] = AccRuleEnum.NEXP.getRuleDescText();
							previousEventPartDetailsObj.setM_bolMatchDone(true);
							setPreviousEventPartDetailsData(previousEventPartDetails,previousEventPartDetailsObj);
							return args;
						}
					}
				}
			}else{
				if(!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
						&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999")
						&& !m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
    							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
						&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
    							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())
						&& !(previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber()))){
					args = new String[4];
					args[0] = AccRuleEnum.NEXP.getAppCostChangeCode();
					args[1] = String.valueOf(previousEventPartDetails.getM_strPartNumber().length());
					args[2] = AccRuleEnum.NEXP.getRuleId();
					args[3] = AccRuleEnum.NEXP.getRuleDescText();
					return args;
				}
			}
		
		} else if(value.compareTo(AccRuleEnum.IHOS)==0){

			//TODO - Logical check for this rule
			if(null!=m_lEnterACCPreviousEventPartDetailsDTO && !m_lEnterACCPreviousEventPartDetailsDTO.isEmpty()){
				charMatchArray = new int[]{13};
				for(int charMatch : charMatchArray){
					for(EnterACCEventPartDetailsDTO previousEventPartDetailsObj : m_lEnterACCPreviousEventPartDetailsDTO){
						if(!previousEventPartDetailsObj.isM_bolMatchDone() 
								//Added here this condition because if the part is a exact match then it will be considered by rules in the else block (if m_lEnterACCPreviousEventPartDetailsDTO is null)
								&& !StringUtils.equals(currentEventPartDetails.getM_strPartNumber().trim(), previousEventPartDetailsObj.getM_strPartNumber().trim())
								&& currentEventPartDetails.getM_strPartNumber().trim().length()>=charMatch
								&& previousEventPartDetailsObj.getM_strPartNumber().trim().length()>=charMatch
								&& currentEventPartDetails.getM_strPartNumber().substring(0, charMatch)
									.matches(previousEventPartDetailsObj.getM_strPartNumber().substring(0, charMatch))
								&& ((m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
										&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
		            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber()))
									|| (!m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
	            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber())  
											&& m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
			            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())))
							//	&& StringUtils.equals(previousEventPartDetailsObj.getM_strProcSectCode(),currentEventPartDetails.getM_strProcSectCode())
								&& StringUtils.equals(previousEventPartDetailsObj.getM_strPlantLocCode(),currentEventPartDetails.getM_strPlantLocCode())
								&& StringUtils.equals(previousEventPartDetailsObj.getM_strModelCatCode(),currentEventPartDetails.getM_strModelCatCode())
								&& currentEventPartDetails.getM_intPartQty().compareTo(previousEventPartDetailsObj.getM_intPartQty()) == 0
								//&& StringUtils.equals(previousEventPartDetailsObj.getM_strPartSectionCode(),currentEventPartDetails.getM_strPartSectionCode())
								){
							args = new String[4];
							args[0] = AccRuleEnum.IHOS.getAppCostChangeCode();
							args[1] = String.valueOf(charMatch);
							args[2] = AccRuleEnum.IHOS.getRuleId();
							args[3] = AccRuleEnum.IHOS.getRuleDescText();
							previousEventPartDetailsObj.setM_bolMatchDone(true);
							setPreviousEventPartDetailsData(previousEventPartDetails,previousEventPartDetailsObj);
							return args;
						}
					}
				}
			}else{
				if((m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
						previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
						&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
    							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber()))
						|| (!m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
    							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber())  
								&& m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber()))){
					args = new String[4];
					args[0] = AccRuleEnum.IHOS.getAppCostChangeCode();
					args[1] = String.valueOf(previousEventPartDetails.getM_strPartNumber().length());
					args[2] = AccRuleEnum.IHOS.getRuleId();
					args[3] = AccRuleEnum.IHOS.getRuleDescText();
					return args;
				}
			}
		} else if(value.compareTo(AccRuleEnum.PCCC)==0){
			args = new String[4];
			args[0] = AccRuleEnum.PCCC.getAppCostChangeCode();
			args[1] = String.valueOf(previousEventPartDetails.getM_strPartNumber().length());
			args[2] = AccRuleEnum.PCCC.getRuleId();
			args[3] = AccRuleEnum.PCCC.getRuleDescText();
			return args;
		}
		
		return args;
	}

	
	//INC0726363  / CPT-357 - show part dropped indicator or added indicator with rules applied

    private String[] assignACCBasedOnRulesPartialPartMatch(AccRuleEnum value, EnterACCEventPartDetailsDTO previousEventPartDetails, 
            EnterACCEventPartDetailsDTO currentEventPartDetails, EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
            EnterACCSuppFEMDMTODTO femdDTO, String baseOrCurrent){
    	String [] args = null;
    	String procSect = null;
    	String suppNo = null;
    	//Check if the rule is enabled
    	if(!value.isEnabled())
    		return args;

    	int[] charMatchArray;

    	if(value.compareTo(AccRuleEnum.EXPN)==0){
    		//TODO - Logical check for this rule
    		charMatchArray = new int[]{13,11,10,8,5};//CPT-1033 expn rule for 13 char match in part added / dropped
    		for(int charMatch : charMatchArray){

    			if(baseOrCurrent.equalsIgnoreCase("CURRENT")){
    				if(currentEventPartDetails.getM_strPartNumber().trim().length()>=charMatch){
    				String[] returnParam = accProcessingBatchDAO.checkIfPartialPartMatchExists(enterACCApplicationsSuppMTOSummaryDVO, 
    						currentEventPartDetails, "CURRENT", femdDTO, charMatch);
    				procSect = returnParam[0];
    				suppNo = returnParam[1];
        			if(!StringUtils.equals("", procSect)){
        				previousEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
        				previousEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
        				previousEventPartDetails.setM_strProcSectCode(procSect);
        				
        				if((previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
            					&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))
            					|| (!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
            							&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))){
            				args = new String[4];
            				args[0] = AccRuleEnum.EXPN.getAppCostChangeCode();
            				args[1] = String.valueOf(charMatch);
            				args[2] = AccRuleEnum.EXPN.getRuleId();
            				args[3] = AccRuleEnum.EXPN.getRuleDescText();
            				return args;
            			}
        				
        			} }
    			}else if(baseOrCurrent.equalsIgnoreCase("BASE")){
    				if(previousEventPartDetails.getM_strPartNumber().trim().length()>=charMatch){
    				String[] returnParam = accProcessingBatchDAO.checkIfPartialPartMatchExists(enterACCApplicationsSuppMTOSummaryDVO, 
    						previousEventPartDetails, "BASE", femdDTO, charMatch);
    				procSect = returnParam[0];
    				suppNo = returnParam[1];
        			if(!StringUtils.equals("", procSect)){
        				currentEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
        				currentEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
        				currentEventPartDetails.setM_strProcSectCode(procSect);
        				
        				if((previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
            					&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))
            					|| (!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
            							&& currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999"))){
            				args = new String[4];
            				args[0] = AccRuleEnum.EXPN.getAppCostChangeCode();
            				args[1] = String.valueOf(charMatch);
            				args[2] = AccRuleEnum.EXPN.getRuleId();
            				args[3] = AccRuleEnum.EXPN.getRuleDescText();
            				return args;
            			}
        				
        				
        			} 
    			}}

	

    			
    		}
    	} else if(value.compareTo(AccRuleEnum.FSTN)==0){
    		if(previousEventPartDetails.getM_strPartNumber().charAt(0)=='9' && currentEventPartDetails.getM_strPartNumber().charAt(0)=='9'){
    			args = new String[4];
    			args[0] = AccRuleEnum.FSTN.getAppCostChangeCode();
    			args[1] = String.valueOf(previousEventPartDetails.getM_strPartNumber().length());
    			args[2] = AccRuleEnum.FSTN.getRuleId();
    			args[3] = AccRuleEnum.FSTN.getRuleDescText();
    			return args;
    		}
    	} else if(value.compareTo(AccRuleEnum.NEXP)==0){
    		//TODO - Logical check for this rule
    		charMatchArray = new int[]{13};
    		for(int charMatch : charMatchArray){

    			if(baseOrCurrent.equalsIgnoreCase("CURRENT")){
    				if(currentEventPartDetails.getM_strPartNumber().trim().length()>=charMatch){
    				String[] returnParam = accProcessingBatchDAO.checkIfPartialPartMatchExists(enterACCApplicationsSuppMTOSummaryDVO, 
    						currentEventPartDetails, "CURRENT", femdDTO, charMatch);
    				procSect = returnParam[0];
    				suppNo = returnParam[1];
        			if(!StringUtils.equals("", procSect)){
        				previousEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
        				previousEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
        				previousEventPartDetails.setM_strProcSectCode(procSect);
        				
        				if(!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
            					&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999")
            					&& !m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
            					&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())
            					&& !(previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber()))){
            				args = new String[4];
            				args[0] = AccRuleEnum.NEXP.getAppCostChangeCode();
            				args[1] = String.valueOf(charMatch);
            				args[2] = AccRuleEnum.NEXP.getRuleId();
            				args[3] = AccRuleEnum.NEXP.getRuleDescText();
            				return args;
            			}
        			} }
    			}else if(baseOrCurrent.equalsIgnoreCase("BASE")){
    				if(previousEventPartDetails.getM_strPartNumber().trim().length()>=charMatch){
    				String[] returnParam = accProcessingBatchDAO.checkIfPartialPartMatchExists(enterACCApplicationsSuppMTOSummaryDVO, 
    						previousEventPartDetails, "BASE", femdDTO, charMatch);
    				procSect = returnParam[0];
    				suppNo = returnParam[1];
        			if(!StringUtils.equals("", procSect)){
        				currentEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
        				currentEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
        				currentEventPartDetails.setM_strProcSectCode(procSect);
        				
            			if(!previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999") 
            					&& !currentEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase("JN9999")
            					&& !m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
            					&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())
            					&& !(previousEventPartDetails.getM_strSupplierNumber().equalsIgnoreCase(currentEventPartDetails.getM_strSupplierNumber()))){
            				args = new String[4];
            				args[0] = AccRuleEnum.NEXP.getAppCostChangeCode();
            				args[1] = String.valueOf(charMatch);
            				args[2] = AccRuleEnum.NEXP.getRuleId();
            				args[3] = AccRuleEnum.NEXP.getRuleDescText();
            				return args;
            			}
        			} 
    			}
    		}

    		}


    	} else if(value.compareTo(AccRuleEnum.IHOS)==0){

    		//TODO - Logical check for this rule
    		charMatchArray = new int[]{13};
    		for(int charMatch : charMatchArray){

    			if(baseOrCurrent.equalsIgnoreCase("CURRENT")){
    				if(currentEventPartDetails.getM_strPartNumber().trim().length()>=charMatch){
    				String[] returnParam = accProcessingBatchDAO.checkIfPartialPartMatchExists(enterACCApplicationsSuppMTOSummaryDVO, 
    						currentEventPartDetails, "CURRENT", femdDTO, charMatch);
    				procSect = returnParam[0];
    				suppNo = returnParam[1];
        			if(!StringUtils.equals("", procSect)){
        				previousEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
        				previousEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
        				previousEventPartDetails.setM_strProcSectCode(procSect);
        				
        				if((m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
    							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
            					&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())
            					|| (!m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
            							&& m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
                    							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())))){
            				args = new String[4];
            				args[0] = AccRuleEnum.IHOS.getAppCostChangeCode();
            				args[1] = String.valueOf(charMatch);
            				args[2] = AccRuleEnum.IHOS.getRuleId();
            				args[3] = AccRuleEnum.IHOS.getRuleDescText();
            				return args;
            			}
        			} }
    			}else if(baseOrCurrent.equalsIgnoreCase("BASE")){
    				if(previousEventPartDetails.getM_strPartNumber().trim().length()>=charMatch){
    				String[] returnParam = accProcessingBatchDAO.checkIfPartialPartMatchExists(enterACCApplicationsSuppMTOSummaryDVO, 
    						previousEventPartDetails, "BASE", femdDTO, charMatch);
    				procSect = returnParam[0];
    				suppNo = returnParam[1];
        			if(!StringUtils.equals("", procSect)){
        				currentEventPartDetails.setM_strPartNumber(currentEventPartDetails.getM_strPartNumber());
        				currentEventPartDetails.setM_strSupplierNumber(!(suppNo.isEmpty()) ? suppNo : currentEventPartDetails.getM_strSupplierNumber());
        				currentEventPartDetails.setM_strProcSectCode(procSect);
        				
            			if((m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
    							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
            					&& !m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())
            					|| (!m_lInHouseSupp.contains(previousEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
            							previousEventPartDetails.getM_strSupplierNumber().substring(1) :  previousEventPartDetails.getM_strSupplierNumber()) 
            							&& m_lInHouseSupp.contains(currentEventPartDetails.getM_strSupplierNumber().substring(0, 1).equals("0") ? 
                    							currentEventPartDetails.getM_strSupplierNumber().substring(1) :  currentEventPartDetails.getM_strSupplierNumber())))){
            				args = new String[4];
            				args[0] = AccRuleEnum.IHOS.getAppCostChangeCode();
            				args[1] = String.valueOf(charMatch);
            				args[2] = AccRuleEnum.IHOS.getRuleId();
            				args[3] = AccRuleEnum.IHOS.getRuleDescText();
            				return args;
            			}
        			} 
    			}
    		}

    		}

    	}

    	return args;
}
	//INC0726363  / CPT-357 - end
	
	/**
	 * This method save the processed ACC data in the staging table(FCACC2)
	 * @param m_lEnterACCSuppSummaryPartLevelDataDTOList
	 * @param m_hmpACCDisplayLabelEffDateDTO
	 * @param m_hmpEnterACCSuppSummaryACCDataDTO
	 */
	private void saveProcessedACCDataInStagingTable(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
			ArrayList<EnterACCSuppSummaryPartLevelDataDTO> m_lEnterACCSuppSummaryPartLevelDataDTOList, 
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpACCDisplayLabelEffDateDTO, 
			Map<EnterACCSuppSummaryPartLevelDataDTO, ArrayList<EnterACCSuppSummaryACCDataDTO>> m_hmpEnterACCSuppSummaryACCDataDTO ) {
		log.info("Entering saveProcessedACCDataInStagingTable() method in "+ CLASS_NAME +".");
		
		//prepare batch insert param for new data and batch delete param for existing data
		Object[] accSaveParam = new Object[60];
		ArrayList<Object[]> accDataToSaveInACC2 = new ArrayList<Object[]>();
		int location=0;//Used to fetch location of ACC Cost object which has BaseOrCurrentEvent parameter
		for(EnterACCSuppSummaryPartLevelDataDTO partLevelObj : m_lEnterACCSuppSummaryPartLevelDataDTOList){
			
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> accCostDisplayEffectiveDateList = m_hmpACCDisplayLabelEffDateDTO.get(partLevelObj).get(0).getM_lenterACCSuppSummaryACCCostDataDTOList();
			ArrayList<EnterACCSuppSummaryACCCostDataDTO> accCostDisplayACCCodeList = m_hmpACCDisplayLabelEffDateDTO.get(partLevelObj).get(1).getM_lenterACCSuppSummaryACCCostDataDTOList();
			
			for(EnterACCSuppSummaryACCDataDTO accDataObj : m_hmpEnterACCSuppSummaryACCDataDTO.get(partLevelObj)){
				location=0;
				for(EnterACCSuppSummaryACCCostDataDTO accCostObj : accDataObj.getM_lenterACCSuppSummaryACCCostDataDTOList()){
					accSaveParam = new Object[61];
					accSaveParam[0] = enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent();
					accSaveParam[1] = enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev();
					accSaveParam[2] = enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent();
					accSaveParam[3] = enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev();
					accSaveParam[4] = partLevelObj.getM_strProcurementGroup();
					accSaveParam[5] = partLevelObj.getM_strSupplierNumber();
					accSaveParam[6] = null!=partLevelObj.getM_strSupplierNumberBaseCurrent() && !partLevelObj.getM_strSupplierNumberBaseCurrent().isEmpty()  ? 
										partLevelObj.getM_strSupplierNumberBaseCurrent() : "";
					accSaveParam[7] = partLevelObj.getM_strSupplierName();
					accSaveParam[8] = partLevelObj.getM_strPlant();
					accSaveParam[9] = partLevelObj.getM_strDesignSectionCode();
					accSaveParam[10] = partLevelObj.getM_strModelCatCode();
					accSaveParam[11] = partLevelObj.getM_decShareRate();
					accSaveParam[12] = partLevelObj.getM_intQty();
					accSaveParam[13] = partLevelObj.getM_strPartNumber();
					accSaveParam[14] = null!=partLevelObj.getM_strPartNumberBaseCurrent() && !partLevelObj.getM_strPartNumberBaseCurrent().isEmpty() ? 
										partLevelObj.getM_strPartNumberBaseCurrent() : "";
					accSaveParam[15] = partLevelObj.getM_strPartName();
					accSaveParam[16] = null!=partLevelObj.getM_strPartACCIndicator() ? partLevelObj.getM_strPartACCIndicator() : "";
					accSaveParam[17] = accDataObj.getM_decPreviousCost();
					accSaveParam[18] = accDataObj.getM_decCurrentCost();
					accSaveParam[19] = accDataObj.getM_decDifferenceCost();
					accSaveParam[20] = accDataObj.getM_decMCCCost();
					accSaveParam[21] = accDataObj.getM_decBalanceCost();
					accSaveParam[22] = accCostObj.getM_decACCCost();
					accSaveParam[23] = null!=accCostObj.getM_strACC() && !accCostObj.getM_strACC().isEmpty() ? 
										accCostObj.getM_strACC() : "";
					accSaveParam[24] = accCostObj.getM_strAccStatus();
					accSaveParam[25] = null!=accCostObj.getM_strRuleId() && !accCostObj.getM_strRuleId().isEmpty() ? 
										accCostObj.getM_strRuleId() : "";
					accSaveParam[26] = null!=accCostObj.getM_strAccRulePartCharMatch() && !accCostObj.getM_strAccRulePartCharMatch().isEmpty() ? 
										accCostObj.getM_strAccRulePartCharMatch().trim(): "";
					accSaveParam[27] = null!=accCostObj.getM_strEffectiveDate() ? accCostObj.getM_strEffectiveDate() : Utility.convertFromUtilDateToStr(Utility.getMonthsFirstDayDate(), "yyyy-MM-dd");
					accSaveParam[28] = null!=accCostObj.getM_strModifiedBy() ? accCostObj.getM_strModifiedBy() : "";
					accSaveParam[29] = null!=accCostObj.getM_strModifiedDate()&&!accCostObj.getM_strModifiedDate().trim().isEmpty() ? accCostObj.getM_strModifiedDate():enterACCApplicationsSuppMTOSummaryDVO.getCreatedTstp();
					
					
					//accCostList for lop this to check the variable
					
					/*for(EnterACCSuppSummaryACCCostDataDTO accCostDisplayDataObj : accCostDisplayEffectiveDateList){
						if(null!=accCostObj.getM_strACC() && !accCostObj.getM_strACC().isEmpty() && null!=accCostDisplayACCCodeList.get(location).getM_strACC() &&
								null!=accCostObj.getM_strEffectiveDate() && null!=accCostDisplayDataObj.getM_strEffectiveDate() &&
								accCostObj.getM_strACC().equalsIgnoreCase(accCostDisplayACCCodeList.get(location).getM_strACC()) 
								&& accCostObj.getM_strEffectiveDate().equalsIgnoreCase(accCostDisplayDataObj.getM_strEffectiveDate())){
							accSaveParam[30] = accCostDisplayACCCodeList.get(location).getM_strBaseOrCurrentEvent();
							accSaveParam[31] = null!=accCostDisplayACCCodeList.get(location).getM_strOriginalBaseOrCurrentEvent() ? accCostDisplayACCCodeList.get(location).getM_strOriginalBaseOrCurrentEvent() : "";
							accSaveParam[32] = accCostDisplayACCCodeList.get(location).getM_strBOMChangeImpactOnACC();
							break;
						} else {
							accSaveParam[30]=accCostObj.getM_strBaseOrCurrentEvent();
							accSaveParam[31] = "";
							accSaveParam[32] = "";
						}
						
						location++;
					}*/
					
					//There is scenario wherein same ACC Code and Effective Date is present for two records (CFCBTS) which needs to be shown on screen.
					//CPT-424 start
					accSaveParam[30] = accCostDisplayACCCodeList.get(location).getM_strBaseOrCurrentEvent();
					accSaveParam[31] = null!=accCostDisplayACCCodeList.get(location).getM_strOriginalBaseOrCurrentEvent() ? accCostDisplayACCCodeList.get(location).getM_strOriginalBaseOrCurrentEvent() : "";					
					//System.out.println("   **************"+a+ " "+b);
					//accSaveParam[30] = accCostObj.getM_strBaseOrCurrentEvent();
					//accSaveParam[31] = accCostObj.getM_strBaseOrCurrentEvent();
					System.out.println("   **************"+accSaveParam[30]+ " "+accSaveParam[31]);
					//CPT-424 end
					accSaveParam[32] = accCostDisplayACCCodeList.get(location).getM_strBOMChangeImpactOnACC();
					
					location++;
					if(null!=accCostObj.getEnterACCSuppSummaryACCCommentsDTO() && null!=accCostObj.getEnterACCSuppSummaryACCCommentsDTO().getM_strACCCommentCode()){
						accSaveParam[33] = accCostObj.getEnterACCSuppSummaryACCCommentsDTO().getM_strACCCommentCode();
						accSaveParam[34] = accCostObj.getEnterACCSuppSummaryACCCommentsDTO().getM_strACCCommentDesc();
						accSaveParam[35] = accCostObj.getEnterACCSuppSummaryACCCommentsDTO().getM_strACCCommentNote();
					} else {
						accSaveParam[33] = "";
						accSaveParam[34] = "";
						accSaveParam[35] = "";
					}
					
					
					accSaveParam[36] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication().getTargetModel():"":"";
					accSaveParam[37] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication().getType():"":"";
					accSaveParam[38] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseFrameApplication().getOption():"":"";
					accSaveParam[39] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication().getTargetModel():"":"";
					accSaveParam[40] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication().getType():"":"";
					accSaveParam[41] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentFrameApplication().getOption():"":"";

					accSaveParam[42] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication().getTargetModel():"":"";
					accSaveParam[43] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication().getType():"":"";
					accSaveParam[44] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseEngineApplication().getOption():"":"";
					accSaveParam[45] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication().getTargetModel():"":"";
					accSaveParam[46] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication().getType():"":"";
					accSaveParam[47] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentEngineApplication().getOption():"":"";

					accSaveParam[48] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication().getTargetModel():"":"";
					accSaveParam[49] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication().getType():"":"";
					accSaveParam[50] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseMissionApplication().getOption():"":"";
					accSaveParam[51] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication().getTargetModel():"":"";
					accSaveParam[52] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication().getType():"":"";
					accSaveParam[53] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentMissionApplication().getOption():"":"";

					accSaveParam[54] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication().getTargetModel():"":"";
					accSaveParam[55] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication().getType():"":"";
					accSaveParam[56] = accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getBaseDifferentialApplication().getOption():"":"";
					accSaveParam[57] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication().getTargetModel()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication().getTargetModel():"":"";
					accSaveParam[58] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication().getType()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication().getType():"":"";
					accSaveParam[59] = accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication()!=null?
							accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication().getOption()!=null?accDataObj.getEnterACCSuppFEMDMTODTO().getCurrentDifferentialApplication().getOption():"":"";
					accSaveParam[60] = partLevelObj.getM_strPartColorCode();
					log.info("Object to be saved - "+Arrays.toString(accSaveParam));
					if((!accCostObj.getM_strAccStatus().equalsIgnoreCase(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.DUMMY_ACC.value)) &&
						!accCostObj.getM_decACCCost().equals(BigDecimal.ZERO)){
						accDataToSaveInACC2.add(accSaveParam);
					}
				}
			}
		}
		//Delete the existing records
		accProcessingBatchDAO.deleteACC2Data(enterACCApplicationsSuppMTOSummaryDVO);
		//Insert new records
		accProcessingBatchDAO.insertACC2Data(accDataToSaveInACC2);
		
		log.info("Exiting saveProcessedACCDataInStagingTable() method in "+ CLASS_NAME +".");
	}
	
	private void setPreviousEventPartDetailsData(EnterACCEventPartDetailsDTO previousEventPartDetails,EnterACCEventPartDetailsDTO previousEventPartDetailsObj){
		previousEventPartDetails.setM_strProcSectCode(previousEventPartDetailsObj.getM_strProcSectCode());
		previousEventPartDetails.setM_strSupplierNumber(previousEventPartDetailsObj.getM_strSupplierNumber());
		previousEventPartDetails.setM_strSupplierName(previousEventPartDetailsObj.getM_strSupplierName());
		previousEventPartDetails.setM_strPlantLocCode(previousEventPartDetailsObj.getM_strPlantLocCode());
		previousEventPartDetails.setM_strPartSectionCode(previousEventPartDetailsObj.getM_strPartSectionCode());
		previousEventPartDetails.setM_strModelCatCode(previousEventPartDetailsObj.getM_strModelCatCode());
		previousEventPartDetails.setM_decShareRatePercent(previousEventPartDetailsObj.getM_decShareRatePercent());
		previousEventPartDetails.setM_intPartQty(previousEventPartDetailsObj.getM_intPartQty());
		previousEventPartDetails.setM_strPartNumber(previousEventPartDetailsObj.getM_strPartNumber());
		previousEventPartDetails.setM_strPartName(previousEventPartDetailsObj.getM_strPartName());
		previousEventPartDetails.setM_strTgtModelDevCodeFrame(previousEventPartDetailsObj.getM_strTgtModelDevCodeFrame());
		previousEventPartDetails.setM_strMTCTypeFrame(previousEventPartDetailsObj.getM_strMTCTypeFrame());
		previousEventPartDetails.setM_strTgtModelDevCodeEngine(previousEventPartDetailsObj.getM_strTgtModelDevCodeEngine());
		previousEventPartDetails.setM_strMTCTypeEngine(previousEventPartDetailsObj.getM_strMTCTypeEngine());
		previousEventPartDetails.setM_strTgtModelDevCodeMission(previousEventPartDetailsObj.getM_strTgtModelDevCodeMission());
		previousEventPartDetails.setM_strMTCTypeMission(previousEventPartDetailsObj.getM_strMTCTypeMission());
		previousEventPartDetails.setM_strTgtModelDevCodeDifferential(previousEventPartDetailsObj.getM_strTgtModelDevCodeDifferential());
		previousEventPartDetails.setM_strMTCTypeDifferential(previousEventPartDetailsObj.getM_strMTCTypeDifferential());
		previousEventPartDetails.setM_decEndCostAmount(previousEventPartDetailsObj.getM_decEndCostAmount());
		previousEventPartDetails.setM_decMCCAmount(previousEventPartDetailsObj.getM_decMCCAmount());
		previousEventPartDetails.setM_strCostChangeCode(previousEventPartDetailsObj.getM_strCostChangeCode());
		previousEventPartDetails.setM_strCostChangeCATCode(previousEventPartDetailsObj.getM_strCostChangeCATCode());
		previousEventPartDetails.setM_bolMatchDone(previousEventPartDetailsObj.isM_bolMatchDone());
		previousEventPartDetails.setM_strPartColorCode(previousEventPartDetailsObj.getM_strPartColorCode());
	}
	
	public void sendEmailNotification(EmailUserDTO emailUser, AccDefinitionDto accDefinition) throws ApplicationException {
		List<EmailNotificationDTO> emailNotificationDTOList = new ArrayList<EmailNotificationDTO>();
		EmailNotificationDTO emailNotificationDTO = new EmailNotificationDTO();
		ArrayList<EmailUserDTO> emailUserDTOList = new ArrayList<EmailUserDTO>();
		
		emailUserDTOList.add(emailUser);

		emailNotificationDTO.setEmailUserList(emailUserDTOList);
		emailNotificationDTO.setEmailsendreason(BatchConstantsIF.EMAIL_SEND_REASON.AccJobCompletedNotification.value());
		emailNotificationDTO.setEmailsubject("ACC Job run completed - "+accDefinition.getDescText());
		emailNotificationDTO.setEmailbodytext("ACC Job - "+accDefinition.getDescText()+" submitted has been completed.");
		emailNotificationDTOList.add(emailNotificationDTO);
		sendEmailNotifications(emailNotificationDTOList);
	}
	
    private List<EmailNotificationDTO> sendEmailNotifications(List<EmailNotificationDTO> emailList) throws ApplicationException{
        List<EmailNotificationDTO> afterEmailSendList = new ArrayList<EmailNotificationDTO>();

        Iterator emailJobItr = emailList.iterator();

        while (emailJobItr.hasNext())
        {
        	EmailNotificationDTO emailNotificationDTO = (EmailNotificationDTO) emailJobItr.next();
        	String signature = BatchConstantsIF.SigFirstLine + BatchConstantsIF.SigSecondLine + BatchConstantsIF.SigLastLine;

        	emailNotificationDTO.setEmailbodytext(
        			(null!=emailNotificationDTO.getEmailsalutation() ? emailNotificationDTO.getEmailsalutation() : "")
                    + emailNotificationDTO.getEmailbodytext() 
                    + signature );

        	emailNotificationDTO = sendNotificationEmail(emailNotificationDTO);
            
            afterEmailSendList.add(emailNotificationDTO);
        }

        return afterEmailSendList;
    }

}
