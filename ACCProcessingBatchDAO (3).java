/**
 * 
 */
package com.honda.cart2.batch.dao;


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.ApplicationException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager; import org.apache.logging.log4j.Logger;

import com.honda.cart2.batch.dvo.acc.EnterACCApplicationsDVO;
import com.honda.cart2.batch.dvo.acc.EnterACCApplicationsSuppMTOSummaryDVO;
import com.honda.cart2.batch.dvo.acc.EnterACCEventPartDetailsDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppFEMDMTODTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryACCDataDetailsDTO;
import com.honda.cart2.batch.dvo.acc.EnterACCSuppSummaryPartLevelDataDTO;
import com.honda.cart2.batch.entity.AccRuleEnum;
import com.honda.cart2.batch.entity.PartMasterDto;
import com.honda.cart2.common.dataaccess.DAOHelper;
import com.honda.cart2.common.dto.AccDefinitionDto;
import com.honda.cart2.common.dto.EmailUserDTO;
import com.honda.cart2.common.util.BatchConstantsIF;
import com.honda.cart2.common.util.Utility;

/**
 * @author vcc90520
 *
 */
public class ACCProcessingBatchDAO extends DAOHelper implements ACCProcessingBatchSQLIF, Serializable {

	private static final long serialVersionUID = 2839866712149583001L;

	/**
     * Logger for logging functionality.
     */
    protected static final Logger log = LogManager.getLogger(ACCProcessingBatchDAO.class);
    private String CLASS_NAME = ACCProcessingBatchDAO.class.getName();


	@SuppressWarnings("rawtypes")
	public List<PartMasterDto> getPartDetails() {
		log.info("Entering method - getPartDetails() in ACCBatchDAO");
		List<Map<String, Object>> results = null;
		ArrayList<PartMasterDto> partMasterDtos = new ArrayList<PartMasterDto>();

		results = getJdbcTemplate().queryForList(replaceSchemaNames(FETCH_PARAMS_FOR_PROCESSING));
		
		PartMasterDto dto = new  PartMasterDto();
		
		for(Map map:results){
			dto.setPART_NO(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("PART_NO")))));
			dto.setPH_COST_EVENT_NAME(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("PH_COST_EVENT_NAME")))));
//			if(!partMasterDtos.contains(dto))
				partMasterDtos.add(dto);
		}
		
		log.info("Returning from method - getPartDetails() in ACCBatchDAO");
		return partMasterDtos;
	}
	
	public void enterACCReloadBudgetControl() {
    		getJdbcTemplate().update(
	    			replaceSchemaNames("UPDATE FCS.FCROL1 SET ROLE_NAME='ACCBACTCHROLE', MAINT_DATE=CURRENT TIMESTAMP WHERE ROLE_ID=48"));
    		
    		/*getJdbcTemplate().update(
	    			replaceSchemaNames("UPDATE FCS.FCUSR1 SET USER_logon_name='AK CART' WHERE user_logon_id_no='VCC47494'"));*/
    }
	
	public List<Map<String, Object>> fetchParamsForProcessing() {
		log.info("Entered fetchParamsForProcessing() method - "+ CLASS_NAME +".");
		List<Map<String, Object>> results = null;
		
		results = getJdbcTemplate().queryForList(replaceSchemaNames(FETCH_PARAMS_FOR_PROCESSING), new Object[] {BatchConstantsIF.ACC_RULES_STATUS.PENDING.value});
				
		log.info("Entered fetchParamsForProcessing() method - "+ CLASS_NAME +".");
		return results;
	}
	
	/** 
     * This method is used to fetch Mission, Engine and Diff MTO based on Frame MTO.
     * @param baseFrameApplicationsDVO
     * @param currentFrameApplicationsDVO
     * @param enterACCApplicationsSuppMTOSummaryDVO
     */
    public EnterACCSuppFEMDMTODTO fetchMissionEngineDiffBasedOnFrame(EnterACCApplicationsDVO baseFrameApplicationsDVO, 
    		EnterACCApplicationsDVO currentFrameApplicationsDVO,
    		EnterACCApplicationsSuppMTOSummaryDVO  enterACCApplicationsSuppMTOSummaryDVO) {
    	log.info("\n Entering method - fetchMissionEngineDiffBasedOnFrame() in "+CLASS_NAME);
    	EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO=new EnterACCSuppFEMDMTODTO();
    		
		List<Map<String,Object>> results = null;
		
		Map<String, Object> femdParameters = new HashMap<String, Object>();
		
		femdParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent().trim());
		femdParameters.put("baseEventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev().trim());
		femdParameters.put("baseTargetModel", baseFrameApplicationsDVO.getTargetModel());
		femdParameters.put("baseType", baseFrameApplicationsDVO.getType());
		femdParameters.put("baseOption", baseFrameApplicationsDVO.getOption());
		
		femdParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim());
		femdParameters.put("currentEventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev().trim());
		femdParameters.put("currentTargetModel", currentFrameApplicationsDVO.getTargetModel());
		femdParameters.put("currentType", currentFrameApplicationsDVO.getType());
		femdParameters.put("currentOption", currentFrameApplicationsDVO.getOption());
		
		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(ENTER_ACC_SUPP_MTO_SUMMARY_FEMD_SQL), femdParameters);
		int checkCtr=0;
		
		enterACCSuppFEMDMTODTO.setPresentInDB(false);
		enterACCSuppFEMDMTODTO.setBaseEngineApplication(new EnterACCApplicationsDVO());
		enterACCSuppFEMDMTODTO.setBaseMissionApplication(new EnterACCApplicationsDVO());
		enterACCSuppFEMDMTODTO.setBaseDifferentialApplication(new EnterACCApplicationsDVO());
		
		enterACCSuppFEMDMTODTO.setCurrentEngineApplication(new EnterACCApplicationsDVO());
		enterACCSuppFEMDMTODTO.setCurrentMissionApplication(new EnterACCApplicationsDVO());
		enterACCSuppFEMDMTODTO.setCurrentDifferentialApplication(new EnterACCApplicationsDVO());
		
		//for loop will run twice.
		for(Map<String,Object> femdObj : results){
			if(((String)femdObj.get("EVENT")).trim().equalsIgnoreCase("B")){//Base
				enterACCSuppFEMDMTODTO.setPresentInDB(true);
				enterACCSuppFEMDMTODTO.setBaseFrameApplication(baseFrameApplicationsDVO);
				
				//TGT_ENG_FR_MOD_CDE, TGT_ENG_FR_TY_CODE, TGT_ENGFR_OPT_CODE,
				enterACCSuppFEMDMTODTO.getBaseEngineApplication().setTargetModel(((String)femdObj.get("TGT_ENG_FR_MOD_CDE")).trim());
				enterACCSuppFEMDMTODTO.getBaseEngineApplication().setType(((String)femdObj.get("TGT_ENG_FR_TY_CODE")).trim());
				enterACCSuppFEMDMTODTO.getBaseEngineApplication().setOption(((String)femdObj.get("TGT_ENGFR_OPT_CODE")).trim());
				
				//TGT_MIS_FR_MOD_CDE, TGT_MIS_FR_TY_CODE, TGT_MISFR_OPT_CODE
				enterACCSuppFEMDMTODTO.getBaseMissionApplication().setTargetModel(((String)femdObj.get("TGT_MIS_FR_MOD_CDE")).trim());
				enterACCSuppFEMDMTODTO.getBaseMissionApplication().setType(((String)femdObj.get("TGT_MIS_FR_TY_CODE")).trim());
				enterACCSuppFEMDMTODTO.getBaseMissionApplication().setOption(((String)femdObj.get("TGT_MISFR_OPT_CODE")).trim());
				
				//TGT_DIF_FR_MOD_CDE, TGT_DIF_FR_TY_CODE, TGT_DIFFR_OPT_CODE
				enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().setTargetModel(((String)femdObj.get("TGT_DIF_FR_MOD_CDE")).trim());
				enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().setType(((String)femdObj.get("TGT_DIF_FR_TY_CODE")).trim());
				enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().setOption(((String)femdObj.get("TGT_DIFFR_OPT_CODE")).trim());
				
			} else{//Current
				enterACCSuppFEMDMTODTO.setPresentInDB(true);
				enterACCSuppFEMDMTODTO.setCurrentFrameApplication(currentFrameApplicationsDVO);
				
				enterACCSuppFEMDMTODTO.getCurrentEngineApplication().setTargetModel(((String)femdObj.get("TGT_ENG_FR_MOD_CDE")).trim());
				enterACCSuppFEMDMTODTO.getCurrentEngineApplication().setType(((String)femdObj.get("TGT_ENG_FR_TY_CODE")).trim());
				enterACCSuppFEMDMTODTO.getCurrentEngineApplication().setOption(((String)femdObj.get("TGT_ENGFR_OPT_CODE")).trim());
				
				enterACCSuppFEMDMTODTO.getCurrentMissionApplication().setTargetModel(((String)femdObj.get("TGT_MIS_FR_MOD_CDE")).trim());
				enterACCSuppFEMDMTODTO.getCurrentMissionApplication().setType(((String)femdObj.get("TGT_MIS_FR_TY_CODE")).trim());
				enterACCSuppFEMDMTODTO.getCurrentMissionApplication().setOption(((String)femdObj.get("TGT_MISFR_OPT_CODE")).trim());
				
				enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().setTargetModel(((String)femdObj.get("TGT_DIF_FR_MOD_CDE")).trim());
				enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().setType(((String)femdObj.get("TGT_DIF_FR_TY_CODE")).trim());
				enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().setOption(((String)femdObj.get("TGT_DIFFR_OPT_CODE")).trim());
			}
			checkCtr++;
		}
    		
    	log.info("\n Exiting method - fetchMissionEngineDiffBasedOnFrame() in "+CLASS_NAME);
    	return enterACCSuppFEMDMTODTO;
    }
    
    /**
     * This method is used for fetching previous events Part Level Details data.
     * @param enterACCApplicationsSuppMTOSummaryDVO
     * @return previousPartData
     */
    public Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> fetchPreviousEventData(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO) {
    	log.info("\n Entering method - fetchPreviousEventData() in "+CLASS_NAME);
    	Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> previousPartData = new HashMap<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>>();
    	
    	ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCEventPartDetailsDTO = new ArrayList<EnterACCEventPartDetailsDTO>();
		List<Map<String,Object>> results = null;
		StringBuilder querySB;
		StringBuilder innerQuerySB;
		String strMCCCodes="";
		String conditionBasedOnCurrency="";
		String procGrpFrom="";
		String procGrpTo="";
			
		procGrpFrom=enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim().isEmpty()?"A":enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim();
		procGrpTo=enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim().isEmpty()?"99":enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim();
		
		//For New Model Events also only MCC to be considered.
		strMCCCodes="'MCC'";
		
		if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("JPY")){
			conditionBasedOnCurrency="=";
		}else{
			conditionBasedOnCurrency="<>";
		}
		
		//for loop responsible for formation of the inner query to pass the MTO selected on the screen as where clause.
		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO : enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
			if(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null){
				innerQuerySB = new StringBuilder();
				innerQuerySB.append(" SELECT " +
						" '"+enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel() +"'" +
						",'"+enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()+"'" +
						",'"+(enterACCSuppFEMDMTODTO.getBaseFrameApplication().getOption()!=null ? enterACCSuppFEMDMTODTO.getBaseFrameApplication().getOption() : "")+"'" +
						" FROM SYSIBM.SYSDUMMY1 ");
				
				querySB = new StringBuilder((ENTER_ACC_SUPP_MTO_SUMMARY_EVENT_PART_LEVEL_DETAILS.replace("@FrameMTOs@", innerQuerySB)).replace("@MCCCodes@", strMCCCodes).replace("--CONDITION_BASED_ON_CURRENCY--", conditionBasedOnCurrency));
				
	    		Map<String, Object> queryParameters = new HashMap<String, Object>();
	    		
	    		queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent().trim());
	    		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev().trim());
	    		queryParameters.put("procSectionFrom", procGrpFrom);
	    		queryParameters.put("procSectionTo", procGrpTo);
	    		
	    		//Removed as not required, since data base only has currency as USD
	    		//queryParameters.put("currency", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency()!=null?enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim():"");
	    		queryParameters.put("currency", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency()!=null?"USD":"");
	    		
	    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
	    		log.info("PREV QUERY: "+ replaceSchemaNames(querySB.toString()));
	    		EnterACCEventPartDetailsDTO enterACCEventPartDetailsOldDTO = null; 
	    		EnterACCEventPartDetailsDTO enterACCEventPartDetailsDTO = null;
	    		EnterACCEventPartDetailsDTO enterACCEventPartDetailsCheckerDTO = null;
	    		String strMapKey=null;
	    		//Below hashmap will check any duplicate records. In New Model Event, there are two records present in EFM1 table for same Frame MTO. We need to consider 
	    		//only one MTo which has more information in ters of EMD MTOs.
	    		HashMap<String, String> h_mapCheckDuplicateRecords=new HashMap<String, String>();
	    		for(Map<String,Object> previousEventPartDetailsObj : results){
	    			//Temporary Excluding duplicate records due to 2 supp name for JN9999
	    			if(!((String.valueOf(previousEventPartDetailsObj.get("SUPPLIER_NO")).trim()).equalsIgnoreCase("JN9999") 
	    					&& (String.valueOf(previousEventPartDetailsObj.get("SUPPLIER_NAME")).trim()).equalsIgnoreCase("JAPAN SUPPLY PARTS"))){
	    				strMapKey=new String();
						strMapKey=((String) previousEventPartDetailsObj.get("PROC_SECT_CODE")).trim()+((String) previousEventPartDetailsObj.get("SUPPLIER_NO")).trim()+
						((String) previousEventPartDetailsObj.get("PLANT_LOC_CODE"))+((String) previousEventPartDetailsObj.get("PART_SECTION_CODE")).trim()+
						((String) previousEventPartDetailsObj.get("MODEL_CAT_CODE")).trim()+((String) previousEventPartDetailsObj.get("PART_NO")).trim()+
						((String) previousEventPartDetailsObj.get("TGT_MODEL_DEV_CODE")).trim()+((String) previousEventPartDetailsObj.get("MTC_TYPE")).trim()+
						((String) previousEventPartDetailsObj.get("COST_CHANGE_CODE")).trim()+((String) previousEventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim()+
						((String) previousEventPartDetailsObj.get("PART_COLOR_CODE")).trim()+
						(Utility.convertFromSqlDateToStr((java.sql.Date) previousEventPartDetailsObj.get("ACT_QUOTE_EFF_DATE"), "yyyy-MM-dd"))+
						(Utility.convertFromSqlDateToStr((java.sql.Date) previousEventPartDetailsObj.get("COST_BEG_EFF_DATE"), "yyyy-MM-dd"));
		    			if(!h_mapCheckDuplicateRecords.containsKey(strMapKey)){
		    				h_mapCheckDuplicateRecords.put(strMapKey, strMapKey);
		    				//Forming new record fetched as an object of Part Details DTO.
		        			enterACCEventPartDetailsCheckerDTO = setEnterACCEventPartDetailsDTOValue(previousEventPartDetailsObj);
		        			
		        			//This if else is to accumulate the End Cost and MCC data in one row as multiple rows are fetched through query.
		        			if(null != enterACCEventPartDetailsOldDTO && enterACCEventPartDetailsOldDTO.equals(enterACCEventPartDetailsCheckerDTO)){
		        				//Added if condition to avoid duplicate supplier name record which results in cost doubling 
	            				//if(enterACCEventPartDetailsOldDTO.getM_strSupplierName().trim()
	            					//	.equalsIgnoreCase(enterACCEventPartDetailsCheckerDTO.getM_strSupplierName().trim())){
	            					if(String.valueOf(previousEventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("BC")){
	    	        					enterACCEventPartDetailsDTO.setM_decEndCostAmount(
	    	        							enterACCEventPartDetailsDTO.getM_decEndCostAmount().add(
	    	        								(String.valueOf(previousEventPartDetailsObj.get("SUPPLIER_NO")).equalsIgnoreCase("JN9999") ? 
	    	        									(BigDecimal)previousEventPartDetailsObj.get("COST_CHANGE_AMT_JPY") : (BigDecimal)previousEventPartDetailsObj.get("COST_CHANGE_AMT")
	    	        										)));
	    	        					
	    	        				} else {
	    	        					/*enterACCEventPartDetailsDTO.setM_decEndCostAmount(
	    	        							enterACCEventPartDetailsDTO.getM_decEndCostAmount().add(
	    	        									(BigDecimal)previousEventPartDetailsObj.get("COST_CHANGE_AMT")));*///Not required as adding of MCC to BC and getting the end cost is handled in BO - findEndCost().
	    	        					
	    	        					enterACCEventPartDetailsDTO.setM_decMCCAmount(
	    	        							enterACCEventPartDetailsDTO.getM_decMCCAmount().add(
	    	        								(String.valueOf(previousEventPartDetailsObj.get("SUPPLIER_NO")).equalsIgnoreCase("JN9999") ? 
	    	        									(BigDecimal)previousEventPartDetailsObj.get("COST_CHANGE_AMT_JPY") : (BigDecimal)previousEventPartDetailsObj.get("COST_CHANGE_AMT")
	    	        										)));
	    	        				}
	            				//}
		        			} else {
		        				//The below logic is to form a map containing MTO as the key and list of part details in a particular MTO.
		        				//Written in Else block means we have the enterACCEventPartDetailsDTO object done preparation, now new object will be created.
		        				enterACCEventPartDetailsOldDTO = setEnterACCEventPartDetailsDTOValue(previousEventPartDetailsObj);
		        				enterACCEventPartDetailsDTO = setEnterACCEventPartDetailsDTOValue(previousEventPartDetailsObj);
		        				
		        				//if(null == enterACCEventPartDetailsDTO){ Commented as confusing why is the if condition present it's not required..
		        					//TODO Test its working once ran. done but still check further during testing
		            				if(previousPartData.containsKey(enterACCSuppFEMDMTODTO)){
		            					previousPartData.get(enterACCSuppFEMDMTODTO).add(enterACCEventPartDetailsDTO);
		            				} else {
		            					m_lEnterACCEventPartDetailsDTO = new ArrayList<EnterACCEventPartDetailsDTO>();
		            					m_lEnterACCEventPartDetailsDTO.add(enterACCEventPartDetailsDTO);
		            					previousPartData.put(enterACCSuppFEMDMTODTO,
		                						m_lEnterACCEventPartDetailsDTO);
		            				}
		        				//}
		        				
		        				
		        			}
		    			}
	    			}
	    		}
			}
		}
    	log.info("\n Exiting method - fetchPreviousEventData() in "+CLASS_NAME);
    	return previousPartData;
    }
    
    /**
     * This method is used for fetching current events Part Level Details data.
     * @param enterACCApplicationsSuppMTOSummaryDVO
     * @return currentPartData
     */
    public Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> fetchCurrentEventData(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO) {
    	log.info("\n Entering method - fetchCurrentEventData() in "+CLASS_NAME);
    	Map<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>> currentPartData = new HashMap<EnterACCSuppFEMDMTODTO, ArrayList<EnterACCEventPartDetailsDTO>>();
    	ArrayList<EnterACCEventPartDetailsDTO> m_lEnterACCEventPartDetailsDTO = new ArrayList<EnterACCEventPartDetailsDTO>();
    		List<Map<String,Object>> results = null;
    		StringBuilder querySB;
    		StringBuilder innerQuerySB = new StringBuilder();
    		String strMCCCodes="MCC";
    		String conditionBasedOnCurrency="";
    		String procGrpFrom="";
    		String procGrpTo="";
    			
    		procGrpFrom=enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim().isEmpty()?"A":enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim();
    		procGrpTo=enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim().isEmpty()?"99":enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim();
    		
    		if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("JPY")){
				conditionBasedOnCurrency="=";
			}else{
				conditionBasedOnCurrency="<>";
			}
    		
    		//for loop responsible for formation of the inner query to pass the MTO selected on the screen as where clause.
    		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO : enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
	    		if(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null){
	    				
	    			innerQuerySB = new StringBuilder();
	    			innerQuerySB.append(" SELECT " +
	    					" '"+enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel() +"'" +
	    					",'"+enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()+"'" +
	    					",'"+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getOption()!=null ? enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getOption() : "")+"'" +
	    					" FROM SYSIBM.SYSDUMMY1 ");
	    			
	    			querySB = new StringBuilder((ENTER_ACC_SUPP_MTO_SUMMARY_EVENT_PART_LEVEL_DETAILS.replace("@FrameMTOs@", innerQuerySB)).replace("@MCCCodes@", "'"+strMCCCodes+"'").replace("--CONDITION_BASED_ON_CURRENCY--", conditionBasedOnCurrency));
	        		
	        		Map<String, Object> queryParameters = new HashMap<String, Object>();
	        		queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim());
	        		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev().trim());
	        		queryParameters.put("procSectionFrom", procGrpFrom);
	        		queryParameters.put("procSectionTo", procGrpTo);
	        		
	        		//Removed as not required, since data base only has currency as USD
	        		//queryParameters.put("currency", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency()!=null?enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim():"");
	        		queryParameters.put("currency", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency()!=null?"USD":"");
	        		
	        		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
	        		log.info("CURR QUERY : "+ replaceSchemaNames(querySB.toString()));
	        		EnterACCEventPartDetailsDTO enterACCEventPartDetailsOldDTO = null; 
	        		EnterACCEventPartDetailsDTO enterACCEventPartDetailsDTO = null;
	        		EnterACCEventPartDetailsDTO enterACCEventPartDetailsCheckerDTO = null;
	        		String strMapKey=null;
		    		//Below hashmap will check any duplicate records. In New Model Event, there are two records present in EFM1 table for same Frame MTO. We need to consider 
		    		//only one MTo which has more information in ters of EMD MTOs.
		    		HashMap<String, String> h_mapCheckDuplicateRecords=new HashMap<String, String>();
	        		for(Map<String,Object> currentEventPartDetailsObj : results){
	        			//Temporary Excluding duplicate records due to 2 supp name for JN9999
	        			if(!((String.valueOf(currentEventPartDetailsObj.get("SUPPLIER_NO")).trim()).equalsIgnoreCase("JN9999") 
	        					&& (String.valueOf(currentEventPartDetailsObj.get("SUPPLIER_NAME")).trim()).equalsIgnoreCase("JAPAN SUPPLY PARTS"))){
	        				strMapKey=new String();
							strMapKey=((String) currentEventPartDetailsObj.get("PROC_SECT_CODE")).trim()+((String) currentEventPartDetailsObj.get("SUPPLIER_NO")).trim()+
							((String) currentEventPartDetailsObj.get("PLANT_LOC_CODE"))+((String) currentEventPartDetailsObj.get("PART_SECTION_CODE")).trim()+
							((String) currentEventPartDetailsObj.get("MODEL_CAT_CODE")).trim()+((String) currentEventPartDetailsObj.get("PART_NO")).trim()+
							((String) currentEventPartDetailsObj.get("TGT_MODEL_DEV_CODE")).trim()+((String) currentEventPartDetailsObj.get("MTC_TYPE")).trim()+
							((String) currentEventPartDetailsObj.get("COST_CHANGE_CODE")).trim()+((String) currentEventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim()+
							((String) currentEventPartDetailsObj.get("PART_COLOR_CODE")).trim()+
							(Utility.convertFromSqlDateToStr((java.sql.Date) currentEventPartDetailsObj.get("ACT_QUOTE_EFF_DATE"), "yyyy-MM-dd"))+
							(Utility.convertFromSqlDateToStr((java.sql.Date) currentEventPartDetailsObj.get("COST_BEG_EFF_DATE"), "yyyy-MM-dd"));
			    			if(!h_mapCheckDuplicateRecords.containsKey(strMapKey)){
			    				h_mapCheckDuplicateRecords.put(strMapKey, strMapKey);
		        				//Forming new record fetched as an object of Part Details DTO.
		            			enterACCEventPartDetailsCheckerDTO = setEnterACCEventPartDetailsDTOValue(currentEventPartDetailsObj);
		            			
		            			//This if else is to accumulate the End Cost and MCC data in one row as multiple rows are fetched through query.
		            			if(null != enterACCEventPartDetailsOldDTO && enterACCEventPartDetailsOldDTO.equals(enterACCEventPartDetailsCheckerDTO)){
		            				//Added if condition to avoid duplicate supplier name record which results in cost doubling 
		            				//if(enterACCEventPartDetailsOldDTO.getM_strSupplierName().trim()
		            						//.equalsIgnoreCase(enterACCEventPartDetailsCheckerDTO.getM_strSupplierName().trim())){
		            					if(String.valueOf(currentEventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("BC")){
			            					enterACCEventPartDetailsDTO.setM_decEndCostAmount(
			            							enterACCEventPartDetailsDTO.getM_decEndCostAmount().add(
			            								(String.valueOf(currentEventPartDetailsObj.get("SUPPLIER_NO")).equalsIgnoreCase("JN9999") ? 
			            									(BigDecimal)currentEventPartDetailsObj.get("COST_CHANGE_AMT_JPY") : (BigDecimal)currentEventPartDetailsObj.get("COST_CHANGE_AMT")
			            										)));
			            					
			            				} else {
			            					/*enterACCEventPartDetailsDTO.setM_decEndCostAmount(
			            							enterACCEventPartDetailsDTO.getM_decEndCostAmount().add(
			            									(BigDecimal)currentEventPartDetailsObj.get("COST_CHANGE_AMT")));*///Not required as adding of MCC to BC and getting the end cost is handled in BO - findEndCost().
			            					
			            					enterACCEventPartDetailsDTO.setM_decMCCAmount(
			            							enterACCEventPartDetailsDTO.getM_decMCCAmount().add(
			            								(String.valueOf(currentEventPartDetailsObj.get("SUPPLIER_NO")).equalsIgnoreCase("JN9999") ? 
			            									(BigDecimal)currentEventPartDetailsObj.get("COST_CHANGE_AMT_JPY") : (BigDecimal)currentEventPartDetailsObj.get("COST_CHANGE_AMT")
			            										)));
			            			//	}
		            				}
		            			} else {
		            				//The below logic is to form a map containing MTO as the key and list of part details in a particular MTO.
		            				//Written in Else block means we have the enterACCEventPartDetailsDTO object done preparation, now new object will be created. 
		            				
		            				enterACCEventPartDetailsOldDTO = setEnterACCEventPartDetailsDTOValue(currentEventPartDetailsObj);
		            				enterACCEventPartDetailsDTO = setEnterACCEventPartDetailsDTOValue(currentEventPartDetailsObj);
		            				
		            				//if(null == enterACCEventPartDetailsDTO){
		            					//TODO Test its working once ran. done but still check further during testing
		                				if(currentPartData.containsKey(enterACCSuppFEMDMTODTO)){
		                					currentPartData.get(enterACCSuppFEMDMTODTO).add(enterACCEventPartDetailsDTO);
		                				} else {
		                					m_lEnterACCEventPartDetailsDTO = new ArrayList<EnterACCEventPartDetailsDTO>();
		                					m_lEnterACCEventPartDetailsDTO.add(enterACCEventPartDetailsDTO);
		                					currentPartData.put(enterACCSuppFEMDMTODTO,
		                    						m_lEnterACCEventPartDetailsDTO);
		                				}
		            				//}
		            				
		            			}
			    			}
	        			}
	        		}
	    		}
    		}
    		
    	log.info("\n Exiting method - fetchCurrentEventData() in "+CLASS_NAME);
    	return currentPartData;
    }
    
    /**
     * This method is the common code for setting the Part details data fetched by the query.
     * @param eventPartDetailsObj
     * @return
     */
    private EnterACCEventPartDetailsDTO setEnterACCEventPartDetailsDTOValue(Map<String,Object> eventPartDetailsObj){
    	return new EnterACCEventPartDetailsDTO(
				String.valueOf(eventPartDetailsObj.get("PROC_SECT_CODE")),
				String.valueOf(eventPartDetailsObj.get("SUPPLIER_NO")),
				String.valueOf(eventPartDetailsObj.get("SUPPLIER_NAME")),
				String.valueOf(eventPartDetailsObj.get("PLANT_LOC_CODE")),
				String.valueOf(eventPartDetailsObj.get("PART_SECTION_CODE")),
				String.valueOf(eventPartDetailsObj.get("MODEL_CAT_CODE")),
				(BigDecimal)eventPartDetailsObj.get("SHARE_RATE_PERCENT"),
				((BigDecimal)eventPartDetailsObj.get("PART_QTY")).intValueExact(),
				String.valueOf(eventPartDetailsObj.get("PART_COLOR_CODE")),
				String.valueOf(eventPartDetailsObj.get("PART_NO")),
				String.valueOf(eventPartDetailsObj.get("BASIC_PART_NAME")),
				String.valueOf(eventPartDetailsObj.get("TGT_MODEL_DEV_CODE")),
				String.valueOf(eventPartDetailsObj.get("MTC_TYPE")),
				String.valueOf(eventPartDetailsObj.get("TGT_ENG_FR_MOD_CDE")),
				String.valueOf(eventPartDetailsObj.get("TGT_ENG_FR_TY_CODE")),
				String.valueOf(eventPartDetailsObj.get("TGT_MIS_FR_MOD_CDE")),
				String.valueOf(eventPartDetailsObj.get("TGT_MIS_FR_TY_CODE")),
				String.valueOf(eventPartDetailsObj.get("TGT_DIF_FR_MOD_CDE")),
				String.valueOf(eventPartDetailsObj.get("TGT_DIF_FR_TY_CODE")),

				//DCC GCC TCC
				String.valueOf(eventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("BC") ? 
						(String.valueOf(eventPartDetailsObj.get("SUPPLIER_NO")).equalsIgnoreCase("JN9999") ? 
								(BigDecimal)eventPartDetailsObj.get("COST_CHANGE_AMT_JPY") : (BigDecimal)eventPartDetailsObj.get("COST_CHANGE_AMT"))
								: new BigDecimal(0.0000),
				
				//ACT events - MCC and New Model Event - DCC GCC TCC (combined as MCC)
				String.valueOf(eventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("MCC") 
					|| String.valueOf(eventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("DCC") 
					|| String.valueOf(eventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("GCC") 
					|| String.valueOf(eventPartDetailsObj.get("COST_CHG_CAT_CODE")).trim().equalsIgnoreCase("TCC") 
						? (String.valueOf(eventPartDetailsObj.get("SUPPLIER_NO")).equalsIgnoreCase("JN9999") ? 
							(BigDecimal)eventPartDetailsObj.get("COST_CHANGE_AMT_JPY") : (BigDecimal)eventPartDetailsObj.get("COST_CHANGE_AMT")) 
							: new BigDecimal(0.0000)
				
		);
    }
    
    public ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> fetchACCData(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCEventPartDetailsDTO currentEventPartDetails, 
    		EnterACCEventPartDetailsDTO previousEventPartDetails, String typeOfMatch, String baseOrCurrentEventData) {
    	//logger.info("\n Entering method - fetchACCData() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		EnterACCSuppSummaryACCDataDetailsDTO enterACCSuppSummaryACCDataDetailsDTO;
		ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList = new ArrayList<EnterACCSuppSummaryACCDataDetailsDTO>();
		
			querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_SUMMARY_EXACT_MATCH_ACC_DATA);
			
			//Append Status in the query based on what user has selected on the screen. Resolved or Unresolved in case both then no Status check required.
			/*querySB.append(
					StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), ApplicationConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES) 
					? " AND ACC.ACC_STATUS ='"+ApplicationConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value+"'" 
							: StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), ApplicationConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES)
							? " AND ACC.ACC_STATUS ='"+ApplicationConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.ACC_APPLIED.value+"'"
									: ""); *///No required handled in BO as cannot restrict the data pick as we required it fo calculation.
			//CPT_1563 Start
			//querySB.append(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH") ? " AND ACC.SUPPLIER_NO_CURR IN (:supplierNumber,:baseSupplierNumber)" : "");
			querySB.append(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH") ? " AND ACC.SUPPLIER_NO_CURR IN (:supplierNumber)" : " AND ACC.SUPPLIER_NO_CURR IN (:supplierNumber) ");
			//CPT_1563 END
			if(!StringUtils.equals(baseOrCurrentEventData, "")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='B'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='C'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT IN ('C', 'S' ) ");
				}
			}
			
			//Only in case of Proc Section Change
			if(StringUtils.equals(typeOfMatch, "PROC_GROUP_CHANGE_MATCH")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.PROC_SECT_CODE= '" +previousEventPartDetails.getM_strProcSectCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					//Do nothing as we have to pick up the ACC for the Same in case any and also current.
				}
				querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
			} else if(StringUtils.equals(typeOfMatch, "DESIGN_SECT_CHANGE_MATCH")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.PART_SECTION_CODE= '" +previousEventPartDetails.getM_strPartSectionCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					//Do nothing as we have to pick up the ACC for the Same in case any and also current.
				}
				//MHC - due to multiple hierarchy change proc group needs to be removed as proc group can be changed from BOM maintenance
				//querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
			}else {
				//MHC - due to multiple hierarchy change proc group not required needs to be removed as this will be called during exact match
				//Design Sec required as it can be different for same part. And it cannot be changed.
				//querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
				querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
			}
			
			//if(StringUtils.equals(typeOfMatch, "PART_COLOR_CODE_CHANGE_MATCH")){

			    String partColorCode = null;

			    if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
			        partColorCode = previousEventPartDetails.getM_strPartColorCode();
			    } else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")){
			        partColorCode = currentEventPartDetails.getM_strPartColorCode();
			    }else{
			        partColorCode = currentEventPartDetails.getM_strPartColorCode();
			    }

			    //  FIX: handle null / empty properly
			    if(partColorCode != null && partColorCode.trim().length() > 0){
			        querySB.append(" AND ACC.PART_COLOR_CODE= '" + partColorCode.trim() + "'");
			    } else {
			        //  IMPORTANT: match NULL in DB
			        querySB.append(" AND (ACC.PART_COLOR_CODE IS NULL OR ACC.PART_COLOR_CODE = '')");
			    }
			//}
			
			querySB.append(" WITH UR ");
			Map<String, Object> queryParameters = new HashMap<String, Object>();
    		
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev()));
    		
    		queryParameters.put("currentTgtModelDevCode",currentEventPartDetails.getM_strTgtModelDevCodeFrame()); 
    		queryParameters.put("currentMTCType", currentEventPartDetails.getM_strMTCTypeFrame());
    		queryParameters.put("baseTgtModelDevCode", previousEventPartDetails.getM_strTgtModelDevCodeFrame());
    		queryParameters.put("baseMTCType", previousEventPartDetails.getM_strMTCTypeFrame());
    		
    		queryParameters.put("currentTgtModelDevCodeEngine",currentEventPartDetails.getM_strTgtModelDevCodeEngine()); 
    		queryParameters.put("currentMTCTypeEngine", currentEventPartDetails.getM_strMTCTypeEngine());
    		queryParameters.put("baseTgtModelDevCodeEngine", previousEventPartDetails.getM_strTgtModelDevCodeEngine());
    		queryParameters.put("baseMTCTypeEngine", previousEventPartDetails.getM_strMTCTypeEngine());
    		
    		queryParameters.put("currentTgtModelDevCodeMission",currentEventPartDetails.getM_strTgtModelDevCodeMission()); 
    		queryParameters.put("currentMTCTypeMission", currentEventPartDetails.getM_strMTCTypeMission());
    		queryParameters.put("baseTgtModelDevCodeMission", previousEventPartDetails.getM_strTgtModelDevCodeMission());
    		queryParameters.put("baseMTCTypeMission", previousEventPartDetails.getM_strMTCTypeMission());
    		
    		queryParameters.put("currentTgtModelDevCodeDiff",currentEventPartDetails.getM_strTgtModelDevCodeDifferential()); 
    		queryParameters.put("currentMTCTypeDiff", currentEventPartDetails.getM_strMTCTypeDifferential());
    		queryParameters.put("baseTgtModelDevCodeDiff", previousEventPartDetails.getM_strTgtModelDevCodeDifferential());
    		queryParameters.put("baseMTCTypeDiff", previousEventPartDetails.getM_strMTCTypeDifferential());
    		
    		queryParameters.put("modelCatCode", currentEventPartDetails.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", currentEventPartDetails.getM_strPlantLocCode());
    		
    		queryParameters.put("partNumberCurrent", currentEventPartDetails.getM_strPartNumber());
    		queryParameters.put("partNumberBase", previousEventPartDetails.getM_strPartNumber());
    		
    		/* Considering only base supplier in order to fetch appropriate ACC:
    		 * 1. In case user does a BOM maintenance and changes the current supplier to same as base supplier then we need to fetch that ACC too.
    		 * 2. In case user does a BOM maintenance and changes the quantity or share rate in the current event and makes it same as base event then we need to fetch that ACC too.
    		 * In both the above scenarios user should be able to view the ACC already present and take appropriate action on the screen.(Either delete, reject based on the status of the ACC.)
    		 */
    		queryParameters.put("baseSupplierNumber", previousEventPartDetails.getM_strSupplierNumber());
    		
    		//Only in case of supplier Change consider current supplier as well
    		//if(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH"))
    			queryParameters.put("supplierNumber", currentEventPartDetails.getM_strSupplierNumber());
    		
    		//queryParameters.put("partSectCode",currentEventPartDetails.getM_strPartSectionCode());
    		log.info("query in fetchACCData -  "+querySB.toString()+" "+queryParameters);
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
    		
    		for(Map<String,Object> accDataObj : results){
    			enterACCSuppSummaryACCDataDetailsDTO = new EnterACCSuppSummaryACCDataDetailsDTO();
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strRuleId((String)accDataObj.get("RULE_ID"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAppCostChangeCode((String)accDataObj.get("APP_COST_CHANGE_CODE"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_decACCAmount((BigDecimal)accDataObj.get("ACC_AMOUNT"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccStatus(String.valueOf((Integer)accDataObj.get("ACC_STATUS")));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccRulePartCharMatch(((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strPartDistinguishingReason((String)accDataObj.get("PART_DISTINGUISHING_REASON"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strEffectiveDate(Utility.convertFromUtilDateToStr((Date)accDataObj.get("EFFECTIVE_DATE"),"MM/dd/yyyy"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedBy((String)accDataObj.get("MODIFIED_BY"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedDate(Utility.convertSqlTimestamptoStringACC((Timestamp)accDataObj.get("MODIFIED_TSTP"),"yyyy-MM-dd HH.mm.ss"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccComments((String)accDataObj.get("ACC_COMMENTS"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentDesc(accDataObj.get("CODE_DESC_TEXT")!=null ? ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[0] :"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentNote(accDataObj.get("CODE_DESC_TEXT")!=null && ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
    					((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[1] :"");//Note to be sub stringed from CODE_DESC_TEXT to be done after reply from business on Codes table.
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strBaseOrCurrentEvent((String)accDataObj.get("IS_BASE_OR_CURRENT_EVENT"));
    			m_lenterACCSuppSummaryACCDataDetailsDTOList.add(enterACCSuppSummaryACCDataDetailsDTO);
    			
    			
    			
    			
    		}
    		
    	//logger.info("\n Exiting method - fetchACCData() in "+CLASS_NAME);
    	return m_lenterACCSuppSummaryACCDataDetailsDTOList;
    }
    
    public List<Map<String,Object>> fetchAllACCForPartDataAndAllMTOS(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO, 
    		EnterACCEventPartDetailsDTO currentEventPartDetails, EnterACCEventPartDetailsDTO previousEventPartDetails, String typeOfMatch, String baseOrCurrentEventData ) {
    	//logger.info("\n Entering method - fetchAllACCForPartDataAndAllMTOS() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		StringBuilder innerQuerySB = new StringBuilder();
		String baseMTOComparison="";
		String currMTOComparison="";
			
			//for loop responsible for formation of the inner query to pass the MTO selected on the screen as where clause.
    		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO : enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
   				innerQuerySB.append(" SELECT " +
        					" '"+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType():"":"")+"'" +
        					" FROM SYSIBM.SYSDUMMY1 ");
    			innerQuerySB.append(" UNION ALL ");
    		}
    		if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "F")){
    			baseMTOComparison="ACC.BASE_TGT_MODEL_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_MODEL_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_MTC_TYPE = CURREPA.MTC_TYPE";
    		}else if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "E")){
    			baseMTOComparison="ACC.BASE_TGT_ENG_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_ENG_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_ENG_MOD_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_ENG_MTC_TYPE = CURREPA.MTC_TYPE";
    		}else if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "M")){
    			baseMTOComparison=" ACC.BASE_TGT_MIS_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_MIS_MTC_TYPE = BASEEPA.MTC_TYPE ";
    			currMTOComparison=" ACC.CURR_TGT_MIS_MOD_DEV_CODE=CURREPA.TGT_MODEL_DEV_CODE AND CURR_MIS_MTC_TYPE = CURREPA.MTC_TYPE  ";
    		}else{
    			baseMTOComparison="ACC.BASE_TGT_DIF_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_DIF_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_DIF_MOD_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_DIF_MTC_TYPE = CURREPA.MTC_TYPE";
    		}
    		//Logic to include/exclude base or Current MTO comparison with EPA
    		String query=ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW;
    		if(StringUtils.equals(typeOfMatch, "PART_ADDED")){
    			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", "").replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_CURRENT_MTO_CONDITION);
    		}else if(StringUtils.equals(typeOfMatch, "PART_DROPPED")){
    			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_BASE_MTO_CONDITION).replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", "");
    		}else{
    			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_BASE_MTO_CONDITION).replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_CURRENT_MTO_CONDITION);
    		}
    		
			querySB = new StringBuilder(query.replace("@allScreenMTOs@", innerQuerySB.substring(0, innerQuerySB.length()-11)).replace("--BASE_MTO_COMPARISON--", baseMTOComparison).replace("--CURR_MTO_COMPARISON--", currMTOComparison));
			querySB.append(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH") || StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME") 
					? " AND ACC.SUPPLIER_NO_CURR IN (:currentSupplierNumber, :baseSupplierNumber)" : ""); //INC0809589  - Include base suppliernumber 
			
			if(!StringUtils.equals(baseOrCurrentEventData, "")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='B'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='C'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT IN ('C', 'S' ) ");
				}
			}
			//MHC - PROC sect not required as for a part supp combo there will always be one single proc group
			//querySB.append(" AND ACC.PROC_SECT_CODE= '"+enterACCSuppSummaryPartLevelDataDTO.getM_strProcurementGroup()+"'");
			
			if(!StringUtils.equals(typeOfMatch, "PART_QTY_CHANGE_MATCH")){
				if(StringUtils.equals(typeOfMatch, "PART_ADDED")){
					querySB.append(" AND CURREPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());
				}else if(StringUtils.equals(typeOfMatch, "PART_DROPPED")){
					querySB.append(" AND BASEEPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());
				}else{
					querySB.append(" AND BASEEPA.PART_QTY = CURREPA.PART_QTY AND CURREPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());
				}
			}
			String finalQuery="";
			if(StringUtils.equals(typeOfMatch, "DESIGN_SECT_CHANGE_MATCH")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "AND ACC.PART_SECTION_CODE = BASEEPA.PART_SECTION_CODE").replace("@Curr_Design_Sect", "");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
				}
			}else{
				finalQuery=querySB.toString().replace("@Base_Design_Sect", "AND ACC.PART_SECTION_CODE = BASEEPA.PART_SECTION_CODE").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
			}
			
			//querySB.append(" ORDER BY ACC.MODIFIED_TSTP ");
			
			Map<String, Object> queryParameters = new HashMap<String, Object>();
			
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent().trim());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev().trim()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev().trim()));
    		queryParameters.put("modelCatCode", enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", enterACCSuppSummaryPartLevelDataDTO.getM_strPlant());
    		
    		queryParameters.put("partNumberCurrent", enterACCSuppSummaryPartLevelDataDTO.getM_strPartNumber());
    		queryParameters.put("partNumberBase", enterACCSuppSummaryPartLevelDataDTO.getM_strPartNumber());
    		
    		//Only in case of supplier Change consider current supplier as well
    		if(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH") || StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")){
    			queryParameters.put("currentSupplierNumber", currentEventPartDetails.getM_strSupplierNumber());
    			queryParameters.put("baseSupplierNumber", previousEventPartDetails.getM_strSupplierNumber());
    		} else {
    			queryParameters.put("baseSupplierNumber", enterACCSuppSummaryPartLevelDataDTO.getM_strSupplierNumber());
    		}
    		
    		queryParameters.put("partSectCode",enterACCSuppSummaryPartLevelDataDTO.getM_strDesignSectionCode());
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(finalQuery), queryParameters);
			
    	//logger.info("\n Exiting method - fetchAllACCForPartDataAndAllMTOS() in "+CLASS_NAME);
    	return results;
    }
    //ACC Report regeneration failure PRB0011972
    public List<Map<String,Object>> fetchAllACCForPartDataAndAllMTOSRemainingUnMatched(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO, 
    		EnterACCEventPartDetailsDTO currentEventPartDetails, EnterACCEventPartDetailsDTO previousEventPartDetails, String typeOfMatch, String baseOrCurrentEventData ) {
    	//logger.info("\n Entering method - fetchAllACCForPartDataAndAllMTOS() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		StringBuilder innerQuerySB = new StringBuilder();
		String baseMTOComparison="";
		String currMTOComparison="";
			
			//for loop responsible for formation of the inner query to pass the MTO selected on the screen as where clause.
    		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO : enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
   				innerQuerySB.append(" SELECT " +
        					" '"+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType():"":"")+"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel():"":"") +"'" +
        					",'"+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType():"":"")+"'" +
        					" FROM SYSIBM.SYSDUMMY1 ");
    			innerQuerySB.append(" UNION ALL ");
    		}
    		if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "F")){
    			baseMTOComparison="ACC.BASE_TGT_MODEL_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_MODEL_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_MTC_TYPE = CURREPA.MTC_TYPE";
    		}else if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "E")){
    			baseMTOComparison="ACC.BASE_TGT_ENG_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_ENG_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_ENG_MOD_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_ENG_MTC_TYPE = CURREPA.MTC_TYPE";
    		}else if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "M")){
    			baseMTOComparison=" ACC.BASE_TGT_MIS_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_MIS_MTC_TYPE = BASEEPA.MTC_TYPE ";
    			currMTOComparison=" ACC.CURR_TGT_MIS_MOD_DEV_CODE=CURREPA.TGT_MODEL_DEV_CODE AND CURR_MIS_MTC_TYPE = CURREPA.MTC_TYPE  ";
    		}else{
    			baseMTOComparison="ACC.BASE_TGT_DIF_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_DIF_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_DIF_MOD_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_DIF_MTC_TYPE = CURREPA.MTC_TYPE";
    		}
    		//Logic to include/exclude base or Current MTO comparison with EPA
    		String query=ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW;
    		if(StringUtils.equals(typeOfMatch, "PART_ADDED")){
    			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", "").replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_CURRENT_MTO_CONDITION);
    		}else if(StringUtils.equals(typeOfMatch, "PART_DROPPED")){
    			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_BASE_MTO_CONDITION).replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", "");
    		}else{
    			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_BASE_MTO_CONDITION).replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_CURRENT_MTO_CONDITION);
    		}
    		
			querySB = new StringBuilder(query.replace("@allScreenMTOs@", innerQuerySB.substring(0, innerQuerySB.length()-11)).replace("--BASE_MTO_COMPARISON--", baseMTOComparison).replace("--CURR_MTO_COMPARISON--", currMTOComparison));
			querySB.append(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH") || StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME") 
					? " AND ACC.SUPPLIER_NO_CURR=:currentSupplierNumber" : "");
			
			if(!StringUtils.equals(baseOrCurrentEventData, "")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='B'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='C'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT IN ('C', 'S' ) ");
				}
			}
			//MHC - PROC sect not required as for a part supp combo there will always be one single proc group
			//querySB.append(" AND ACC.PROC_SECT_CODE= '"+enterACCSuppSummaryPartLevelDataDTO.getM_strProcurementGroup()+"'");
			
			if(!StringUtils.equals(typeOfMatch, "PART_QTY_CHANGE_MATCH")){
				if(StringUtils.equals(typeOfMatch, "PART_ADDED")){
					querySB.append(" AND CURREPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());
				}else if(StringUtils.equals(typeOfMatch, "PART_DROPPED")){
					querySB.append(" AND BASEEPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());
				}else{
					querySB.append(" AND BASEEPA.PART_QTY = CURREPA.PART_QTY AND CURREPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());
				}
			}
			String finalQuery="";
			if(StringUtils.equals(typeOfMatch, "DESIGN_SECT_CHANGE_MATCH")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "AND ACC.PART_SECTION_CODE = BASEEPA.PART_SECTION_CODE").replace("@Curr_Design_Sect", "");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
				}
			}else{
				finalQuery=querySB.toString().replace("@Base_Design_Sect", "AND ACC.PART_SECTION_CODE = BASEEPA.PART_SECTION_CODE").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
			}
			
			//querySB.append(" ORDER BY ACC.MODIFIED_TSTP ");
			
			Map<String, Object> queryParameters = new HashMap<String, Object>();
			
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent().trim());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev().trim()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev().trim()));
    		queryParameters.put("modelCatCode", enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", enterACCSuppSummaryPartLevelDataDTO.getM_strPlant());
    		
    		queryParameters.put("partNumberCurrent", currentEventPartDetails.getM_strPartNumber());
    		queryParameters.put("partNumberBase", previousEventPartDetails.getM_strPartNumber());
    		
    		//Only in case of supplier Change consider current supplier as well
    		if(StringUtils.equals(typeOfMatch, "SUPP_CHANGE_MATCH") || StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")){
    			queryParameters.put("currentSupplierNumber", currentEventPartDetails.getM_strSupplierNumber());
    			queryParameters.put("baseSupplierNumber", previousEventPartDetails.getM_strSupplierNumber());
    		} else {
    			queryParameters.put("baseSupplierNumber", enterACCSuppSummaryPartLevelDataDTO.getM_strSupplierNumber());
    		}
    		
    		queryParameters.put("partSectCode",enterACCSuppSummaryPartLevelDataDTO.getM_strDesignSectionCode());
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(finalQuery), queryParameters);
			
    	//logger.info("\n Exiting method - fetchAllACCForPartDataAndAllMTOS() in "+CLASS_NAME);
    	return results;
    }
    public ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> fetchACCDataForUnMatched(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCEventPartDetailsDTO currentEventPartDetails, 
    		EnterACCEventPartDetailsDTO previousEventPartDetails, String currentOrBaseEvent) {//CPT-357
    	//logger.info("\n Entering method - fetchACCDataForUnMatched() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		EnterACCSuppSummaryACCDataDetailsDTO enterACCSuppSummaryACCDataDetailsDTO;
		ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList = new ArrayList<EnterACCSuppSummaryACCDataDetailsDTO>();
		
			querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_SUMMARY_UN_MATCH_ACC_DATA);
			
			Map<String, Object> queryParameters = new HashMap<String, Object>();
    		
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev()));
    		
    		queryParameters.put("currentTgtModelDevCode",currentEventPartDetails.getM_strTgtModelDevCodeFrame()); 
    		queryParameters.put("currentMTCType", currentEventPartDetails.getM_strMTCTypeFrame());
    		queryParameters.put("baseTgtModelDevCode", previousEventPartDetails.getM_strTgtModelDevCodeFrame());
    		queryParameters.put("baseMTCType", previousEventPartDetails.getM_strMTCTypeFrame());
    		
    		queryParameters.put("currentTgtModelDevCodeEngine",currentEventPartDetails.getM_strTgtModelDevCodeEngine()); 
    		queryParameters.put("currentMTCTypeEngine", currentEventPartDetails.getM_strMTCTypeEngine());
    		queryParameters.put("baseTgtModelDevCodeEngine", previousEventPartDetails.getM_strTgtModelDevCodeEngine());
    		queryParameters.put("baseMTCTypeEngine", previousEventPartDetails.getM_strMTCTypeEngine());
    		
    		queryParameters.put("currentTgtModelDevCodeMission",currentEventPartDetails.getM_strTgtModelDevCodeMission()); 
    		queryParameters.put("currentMTCTypeMission", currentEventPartDetails.getM_strMTCTypeMission());
    		queryParameters.put("baseTgtModelDevCodeMission", previousEventPartDetails.getM_strTgtModelDevCodeMission());
    		queryParameters.put("baseMTCTypeMission", previousEventPartDetails.getM_strMTCTypeMission());
    		
    		queryParameters.put("currentTgtModelDevCodeDiff",currentEventPartDetails.getM_strTgtModelDevCodeDifferential()); 
    		queryParameters.put("currentMTCTypeDiff", currentEventPartDetails.getM_strMTCTypeDifferential());
    		queryParameters.put("baseTgtModelDevCodeDiff", previousEventPartDetails.getM_strTgtModelDevCodeDifferential());
    		queryParameters.put("baseMTCTypeDiff", previousEventPartDetails.getM_strMTCTypeDifferential());
    		
    		queryParameters.put("modelCatCode", currentEventPartDetails.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", currentEventPartDetails.getM_strPlantLocCode());
    		
    		queryParameters.put("partNumberCurrent", currentEventPartDetails.getM_strPartNumber());
    		queryParameters.put("partNumberBase", previousEventPartDetails.getM_strPartNumber());
    		
    		queryParameters.put("baseSupplierNumber", previousEventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("currentSupplierNumber", currentEventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("procSectCode",currentEventPartDetails.getM_strProcSectCode());
    		queryParameters.put("baseProcSectCode",previousEventPartDetails.getM_strProcSectCode());//CPT-357
    		queryParameters.put("partSectCode",currentEventPartDetails.getM_strPartSectionCode());
    		queryParameters.put("currOrBaseEvent",currentOrBaseEvent);//CPT-357
    		
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
    		
    		for(Map<String,Object> accDataObj : results){
    			enterACCSuppSummaryACCDataDetailsDTO = new EnterACCSuppSummaryACCDataDetailsDTO();
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strRuleId((String)accDataObj.get("RULE_ID"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAppCostChangeCode((String)accDataObj.get("APP_COST_CHANGE_CODE"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_decACCAmount((BigDecimal)accDataObj.get("ACC_AMOUNT"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccStatus(String.valueOf((Integer)accDataObj.get("ACC_STATUS")));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccRulePartCharMatch(((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strPartDistinguishingReason((String)accDataObj.get("PART_DISTINGUISHING_REASON"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strEffectiveDate(Utility.convertFromUtilDateToStr((Date)accDataObj.get("EFFECTIVE_DATE"),"MM/dd/yyyy"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedBy((String)accDataObj.get("MODIFIED_BY"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedDate(Utility.convertSqlTimestamptoStringACC((Timestamp)accDataObj.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccComments((String)accDataObj.get("ACC_COMMENTS"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentDesc(accDataObj.get("CODE_DESC_TEXT")!=null ? ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[0] :"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentNote(accDataObj.get("CODE_DESC_TEXT")!=null && ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
    					((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[1] :"");//Note to be sub stringed from CODE_DESC_TEXT to be done after reply from business on Codes table.
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strBaseOrCurrentEvent((String)accDataObj.get("IS_BASE_OR_CURRENT_EVENT"));
    			m_lenterACCSuppSummaryACCDataDetailsDTOList.add(enterACCSuppSummaryACCDataDetailsDTO);
    			
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedDate(Utility.convertSqlTimestamptoStringACC((Timestamp)accDataObj.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"));
    		}
    		
    	//logger.info("\n Exiting method - fetchACCDataForUnMatched() in "+CLASS_NAME);
    	return m_lenterACCSuppSummaryACCDataDetailsDTOList;
    }
    
    public ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> fetchACCDataForMultipleIndicatorChange(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCEventPartDetailsDTO currentEventPartDetails, 
    		EnterACCEventPartDetailsDTO previousEventPartDetails, ArrayList<String> indicator, String baseOrCurrentEventData) {
    	log.info("\n Entering method - fetchACCDataForMultipleIndicatorChange() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		EnterACCSuppSummaryACCDataDetailsDTO enterACCSuppSummaryACCDataDetailsDTO;
		ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList = new ArrayList<EnterACCSuppSummaryACCDataDetailsDTO>();
		
			querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_SUMMARY_EXACT_MATCH_ACC_DATA);
			
			//Append Status in the query based on what user has selected on the screen. Resolved or Unresolved in case both then no Status check required.
			/*querySB.append(
					StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), ApplicationConstantsIF.ACC_APP_CONSTANTS.UNRESOLVED_BALANCES) 
					? " AND ACC.ACC_STATUS ='"+ApplicationConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.PENDING_APPROVAL.value+"'" 
							: StringUtils.equals(enterACCApplicationsSuppMTOSummaryDVO.getM_strDataToDisplay(), ApplicationConstantsIF.ACC_APP_CONSTANTS.RESOLVED_BALANCES)
							? " AND ACC.ACC_STATUS ='"+ApplicationConstantsIF.ACC_APP_CONSTANTS.ACC_STATUS.ACC_APPLIED.value+"'"
									: ""); *///No required handled in BO as cannot restrict the data pick as we required it fo calculation.
			//CPT-1563 start
			//querySB.append(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()) ? " AND ACC.SUPPLIER_NO_CURR IN (:supplierNumber,:baseSupplierNumber)" : "");
			querySB.append(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()) ? " AND ACC.SUPPLIER_NO_CURR IN (:supplierNumber)" : "");
			//CPT-1563 end
			if(!StringUtils.equals(baseOrCurrentEventData, "")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='B'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='C'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT IN ('C', 'S' ) ");
				}
			}
			
			//Only in case of Proc Section Change
			if(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PROC_GROUP_CHANGE.value())){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.PROC_SECT_CODE= '" +previousEventPartDetails.getM_strProcSectCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					//Do nothing as we have to pick up the ACC for the Same in case any and also current.
				}
				querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
			} else if(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value())){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.PART_SECTION_CODE= '" +previousEventPartDetails.getM_strPartSectionCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					//Do nothing as we have to pick up the ACC for the Same in case any and also current.
				}
				//MHC - due to multiple hierarchy change proc group needs to be removed as proc group can be changed from BOM maintenance
				//querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
			}else if(!indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.PART_COLOR_CODE_CHANGE.value())){
				
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					if(!previousEventPartDetails.getM_strPartColorCode().equals("")&& previousEventPartDetails.getM_strPartColorCode()!=null){
						querySB.append(" AND ACC.PART_COLOR_CODE= '" +previousEventPartDetails.getM_strPartColorCode()+"'");
					}
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					if(!currentEventPartDetails.getM_strPartColorCode().equals("")&& currentEventPartDetails.getM_strPartColorCode()!=null){
						querySB.append(" AND ACC.PART_COLOR_CODE= '" +currentEventPartDetails.getM_strPartColorCode()+"'");
					}
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					//Do nothing as we have to pick up the ACC for the Same in case any and also current.
				}
				//PSCC-5645 - End
				//MHC - due to multiple hierarchy change proc group needs to be removed as proc group can be changed from BOM maintenance
				//querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
			}else {
				if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					//Do nothing as we have to pick up the ACC for the Same in case any and also current.
				} else {
					querySB.append(" AND ACC.PROC_SECT_CODE= '" +currentEventPartDetails.getM_strProcSectCode()+"'");
					querySB.append(" AND ACC.PART_SECTION_CODE= '" +currentEventPartDetails.getM_strPartSectionCode()+"'");
				}
			}
			
			Map<String, Object> queryParameters = new HashMap<String, Object>();
    		
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev()));
    		
    		queryParameters.put("currentTgtModelDevCode",currentEventPartDetails.getM_strTgtModelDevCodeFrame()); 
    		queryParameters.put("currentMTCType", currentEventPartDetails.getM_strMTCTypeFrame());
    		queryParameters.put("baseTgtModelDevCode", previousEventPartDetails.getM_strTgtModelDevCodeFrame());
    		queryParameters.put("baseMTCType", previousEventPartDetails.getM_strMTCTypeFrame());
    		
    		queryParameters.put("currentTgtModelDevCodeEngine",currentEventPartDetails.getM_strTgtModelDevCodeEngine()); 
    		queryParameters.put("currentMTCTypeEngine", currentEventPartDetails.getM_strMTCTypeEngine());
    		queryParameters.put("baseTgtModelDevCodeEngine", previousEventPartDetails.getM_strTgtModelDevCodeEngine());
    		queryParameters.put("baseMTCTypeEngine", previousEventPartDetails.getM_strMTCTypeEngine());
    		
    		queryParameters.put("currentTgtModelDevCodeMission",currentEventPartDetails.getM_strTgtModelDevCodeMission()); 
    		queryParameters.put("currentMTCTypeMission", currentEventPartDetails.getM_strMTCTypeMission());
    		queryParameters.put("baseTgtModelDevCodeMission", previousEventPartDetails.getM_strTgtModelDevCodeMission());
    		queryParameters.put("baseMTCTypeMission", previousEventPartDetails.getM_strMTCTypeMission());
    		
    		queryParameters.put("currentTgtModelDevCodeDiff",currentEventPartDetails.getM_strTgtModelDevCodeDifferential()); 
    		queryParameters.put("currentMTCTypeDiff", currentEventPartDetails.getM_strMTCTypeDifferential());
    		queryParameters.put("baseTgtModelDevCodeDiff", previousEventPartDetails.getM_strTgtModelDevCodeDifferential());
    		queryParameters.put("baseMTCTypeDiff", previousEventPartDetails.getM_strMTCTypeDifferential());
    		
    		queryParameters.put("modelCatCode", currentEventPartDetails.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", currentEventPartDetails.getM_strPlantLocCode());
    		
    		queryParameters.put("partNumberCurrent", currentEventPartDetails.getM_strPartNumber());
    		queryParameters.put("partNumberBase", previousEventPartDetails.getM_strPartNumber());
    		
    		/* Considering only base supplier in order to fetch appropriate ACC:
    		 * 1. In case user does a BOM maintenance and changes the current supplier to same as base supplier then we need to fetch that ACC too.
    		 * 2. In case user does a BOM maintenance and changes the quantity or share rate in the current event and makes it same as base event then we need to fetch that ACC too.
    		 * In both the above scenarios user should be able to view the ACC already present and take appropriate action on the screen.(Either delete, reject based on the status of the ACC.)
    		 */
    		queryParameters.put("baseSupplierNumber", previousEventPartDetails.getM_strSupplierNumber());
    		
    		//Only in case of supplier Change consider current supplier as well
    		if(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()))
    			queryParameters.put("supplierNumber", currentEventPartDetails.getM_strSupplierNumber());
    		
    		//queryParameters.put("partSectCode",currentEventPartDetails.getM_strPartSectionCode());
    		log.info("fetchACCDataForMultipleIndicatorChange query -"+querySB.toString()+" params -"+queryParameters);
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
    		
    		for(Map<String,Object> accDataObj : results){
    			enterACCSuppSummaryACCDataDetailsDTO = new EnterACCSuppSummaryACCDataDetailsDTO();
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strRuleId((String)accDataObj.get("RULE_ID"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAppCostChangeCode((String)accDataObj.get("APP_COST_CHANGE_CODE"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_decACCAmount((BigDecimal)accDataObj.get("ACC_AMOUNT"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccStatus(String.valueOf((Integer)accDataObj.get("ACC_STATUS")));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccRulePartCharMatch(((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strPartDistinguishingReason((String)accDataObj.get("PART_DISTINGUISHING_REASON"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strEffectiveDate(Utility.convertFromUtilDateToStr((Date)accDataObj.get("EFFECTIVE_DATE"),"MM/dd/yyyy"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedBy((String)accDataObj.get("MODIFIED_BY"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedDate(Utility.convertSqlTimestamptoStringACC((Timestamp)accDataObj.get("MODIFIED_TSTP"),"yyyy-MM-dd HH.mm.ss"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccComments((String)accDataObj.get("ACC_COMMENTS"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentDesc(accDataObj.get("CODE_DESC_TEXT")!=null ? ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[0] :"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentNote(accDataObj.get("CODE_DESC_TEXT")!=null && ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
    					((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[1] :"");//Note to be sub stringed from CODE_DESC_TEXT to be done after reply from business on Codes table.
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strBaseOrCurrentEvent((String)accDataObj.get("IS_BASE_OR_CURRENT_EVENT"));
    			m_lenterACCSuppSummaryACCDataDetailsDTOList.add(enterACCSuppSummaryACCDataDetailsDTO);
    			
    		}
    		
    	log.info("\n Exiting method - fetchACCDataForMultipleIndicatorChange() in "+CLASS_NAME);
    	return m_lenterACCSuppSummaryACCDataDetailsDTOList;
    }
    
    public List<Map<String,Object>> fetchAllACCForPartDataAndAllMTOSForMultipleIndicatorChange(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCSuppSummaryPartLevelDataDTO enterACCSuppSummaryPartLevelDataDTO, 
    		EnterACCEventPartDetailsDTO currentEventPartDetails, EnterACCEventPartDetailsDTO previousEventPartDetails, 
    		ArrayList<String> indicator, String baseOrCurrentEventData ) {
    	//logger.info("\n Entering method - fetchAllACCForPartDataAndAllMTOS() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		StringBuilder innerQuerySB = new StringBuilder();
		String baseMTOComparison="";
		String currMTOComparison="";
			
			//for loop responsible for formation of the inner query to pass the MTO selected on the screen as where clause.
    		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO : enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
   				innerQuerySB.append(" SELECT " +
        					" '"+enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType()+"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel() +"'" +
        					",'"+enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType()+"'" +
        					" FROM SYSIBM.SYSDUMMY1 ");
    			innerQuerySB.append(" UNION ALL ");
    		}
    		if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "F")){
    			baseMTOComparison="ACC.BASE_TGT_MODEL_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_MODEL_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_MTC_TYPE = CURREPA.MTC_TYPE";
    		}else if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "E")){
    			baseMTOComparison="ACC.BASE_TGT_ENG_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_ENG_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_ENG_MOD_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_ENG_MTC_TYPE = CURREPA.MTC_TYPE";
    		}else if(StringUtils.equals(enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode(), "M")){
    			baseMTOComparison=" ACC.BASE_TGT_MIS_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_MIS_MTC_TYPE = BASEEPA.MTC_TYPE ";
    			currMTOComparison=" ACC.CURR_TGT_MIS_MOD_DEV_CODE=CURREPA.TGT_MODEL_DEV_CODE AND CURR_MIS_MTC_TYPE = CURREPA.MTC_TYPE  ";
    		}else{
    			baseMTOComparison="ACC.BASE_TGT_DIF_MOD_DEV_CODE= BASEEPA.TGT_MODEL_DEV_CODE AND ACC.BASE_DIF_MTC_TYPE = BASEEPA.MTC_TYPE";
        		currMTOComparison="ACC.CURR_TGT_DIF_MOD_DEV_CODE= CURREPA.TGT_MODEL_DEV_CODE AND ACC.CURR_DIF_MTC_TYPE = CURREPA.MTC_TYPE";
    		}
    		//Logic to include/exclude base or Current MTO comparison with EPA. In this case Both will be considered
    		String query=ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW;
   			query=query.replace("--CONDITION_TO_COMPARE_BASE_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_BASE_MTO_CONDITION).replace("--CONDITION_TO_COMPARE_CURRENT_MTO_WITH_EPA--", ENTER_ACC_SUPP_MTO_SUMMARY_ALL_ACC_FORAROW_CURRENT_MTO_CONDITION);
			querySB = new StringBuilder(query.replace("@allScreenMTOs@", innerQuerySB.substring(0, innerQuerySB.length()-11)).replace("--BASE_MTO_COMPARISON--", baseMTOComparison).replace("--CURR_MTO_COMPARISON--", currMTOComparison));
			querySB.append(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value()) 
					|| StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME") 
					? " AND ACC.SUPPLIER_NO_CURR=:currentSupplierNumber" : "");
			
			if(!StringUtils.equals(baseOrCurrentEventData, "")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='B'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='C'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT IN ('C', 'S' ) ");
				}
			}
			//MHC - PROC sect not required as for a part supp combo there will always be one single proc group
			//querySB.append(" AND ACC.PROC_SECT_CODE= '"+enterACCSuppSummaryPartLevelDataDTO.getM_strProcurementGroup()+"'");
			
			if(!indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.QTY_CHANGE.value()) ){
				querySB.append(" AND BASEEPA.PART_QTY = CURREPA.PART_QTY AND CURREPA.PART_QTY= " + enterACCSuppSummaryPartLevelDataDTO.getM_intQty().toString());	
			}
		    
			String finalQuery="";
			if(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.DESIGN_SECTION_CHANGE.value()) ){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "AND ACC.PART_SECTION_CODE = BASEEPA.PART_SECTION_CODE").replace("@Curr_Design_Sect", "");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					finalQuery=querySB.toString().replace("@Base_Design_Sect", "").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
				}
			}else{
				finalQuery=querySB.toString().replace("@Base_Design_Sect", "AND ACC.PART_SECTION_CODE = BASEEPA.PART_SECTION_CODE").replace("@Curr_Design_Sect", "AND ACC.PART_SECTION_CODE = CURREPA.PART_SECTION_CODE");
			}
			
			//querySB.append(" ORDER BY ACC.MODIFIED_TSTP ");
			
			Map<String, Object> queryParameters = new HashMap<String, Object>();
			
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent().trim());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev().trim()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev().trim()));
    		queryParameters.put("modelCatCode", enterACCSuppSummaryPartLevelDataDTO.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", enterACCSuppSummaryPartLevelDataDTO.getM_strPlant());
    		
    		queryParameters.put("partNumberCurrent", enterACCSuppSummaryPartLevelDataDTO.getM_strPartNumber());
    		queryParameters.put("partNumberBase", enterACCSuppSummaryPartLevelDataDTO.getM_strPartNumber());
    		
    		//Only in case of supplier Change consider current supplier as well
    		if(indicator.contains(BatchConstantsIF.ACC_APP_CONSTANTS.ACC_PART_INDICATOR.SUPPLIER_CHANGE.value())  || StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")){
    			queryParameters.put("currentSupplierNumber", currentEventPartDetails.getM_strSupplierNumber());
    			queryParameters.put("baseSupplierNumber", previousEventPartDetails.getM_strSupplierNumber());
    		} else {
    			queryParameters.put("baseSupplierNumber", enterACCSuppSummaryPartLevelDataDTO.getM_strSupplierNumber());
    		}
    		
    		queryParameters.put("partSectCode",enterACCSuppSummaryPartLevelDataDTO.getM_strDesignSectionCode());
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(finalQuery), queryParameters);
			
    	//logger.info("\n Exiting method - fetchAllACCForPartDataAndAllMTOS() in "+CLASS_NAME);
    	return results;
    
    }
    
    /**
     * This method checks if there is a proc section change for a part between current and base event.
     * @param enterACCApplicationsSuppMTOSummaryDVO
     * @param eventPartDetails
     * @param baseOrCurrentEventData
     * @return
     */
    public String[] checkifProcSectionIsChanged(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO,
    		EnterACCEventPartDetailsDTO eventPartDetails, String baseOrCurrentEventData, EnterACCSuppFEMDMTODTO femdDTO) {
    	//logger.info("\n Entering method - checkifProcSectionIsChanged() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
    	String[] returnParam = new String[2];
    	StringBuilder querySB;
    	BigDecimal endCostAmt = new BigDecimal("0.0000");
    	BigDecimal endMCCAmt = new BigDecimal("0.0000");
    	//CPT-1033 JN9999 to be consider for JPY reports
    	String conditionBasedOnCurrency="";
		if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("JPY")){
			conditionBasedOnCurrency="=";
		}else{
			conditionBasedOnCurrency="<>";
		}
    	querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_SUMMARY_PROC_SECT_CODE_CHANGE.replace("--CONDITION_BASED_ON_CURRENCY--", conditionBasedOnCurrency));
    	//CPT-1033 end
    	Map<String, Object> queryParameters = new HashMap<String, Object>();
    	if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")){
    		queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent());
    		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev());
    		queryParameters.put("partNumber", eventPartDetails.getM_strPartNumber());
    		//queryParameters.put("suppNumber", eventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("modelCatCode", eventPartDetails.getM_strModelCatCode());
    		queryParameters.put("modelDevCode", 
    				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getBaseFrameApplication().getTargetModel()
    						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getBaseEngineApplication().getTargetModel()
    								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getBaseMissionApplication().getTargetModel()
    										: femdDTO.getBaseDifferentialApplication().getTargetModel());
    		queryParameters.put("mtcType", 
    				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getBaseFrameApplication().getType()
    						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getBaseEngineApplication().getType()
    								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getBaseMissionApplication().getType()
    										: femdDTO.getBaseDifferentialApplication().getType());
    		//CPT-1089 start	
    		/*queryParameters.put("procSectionFrom", enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim());
    		queryParameters.put("procSectionTo", enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim());*/
    		queryParameters.put("procSection", eventPartDetails.getM_strProcSectCode().trim());
    		//CPT-1089 - end
    	} else {
    		queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent());
    		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev());
    		queryParameters.put("partNumber", eventPartDetails.getM_strPartNumber());
    		//queryParameters.put("suppNumber", eventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("modelCatCode", eventPartDetails.getM_strModelCatCode());
    		queryParameters.put("modelDevCode", 
    				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getCurrentFrameApplication().getTargetModel()
    						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getCurrentEngineApplication().getTargetModel()
    								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getCurrentMissionApplication().getTargetModel()
    										: femdDTO.getCurrentDifferentialApplication().getTargetModel());
    		queryParameters.put("mtcType", 
    				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getCurrentFrameApplication().getType()
    						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getCurrentEngineApplication().getType()
    								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getCurrentMissionApplication().getType()
    										: femdDTO.getCurrentDifferentialApplication().getType());
    		//CPT-1089 start
    		/*queryParameters.put("procSectionFrom", enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom().trim());
    		queryParameters.put("procSectionTo", enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo().trim());*/
    		queryParameters.put("procSection", eventPartDetails.getM_strProcSectCode().trim());
    		//CPT-1089 end
    	}
    	returnParam[0]="";
    	results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
    	if(results!=null && !results.isEmpty()){
    	//CPT-524 start consider part only if it has variance
    		String procSecCode = !(StringUtils.equals(eventPartDetails.getM_strProcSectCode().trim(), (String)results.get(0).get("PROC_SECT_CODE"))) ? 
    				(String)results.get(0).get("PROC_SECT_CODE") : "";
    				String suppNo = !(StringUtils.equals(eventPartDetails.getM_strSupplierNumber().trim(), (String)results.get(0).get("SUPPLIER_NO"))) ? 
    						(String)results.get(0).get("SUPPLIER_NO") : "";
    						String costChgCatCode = (String) results.get(0).get("COST_CHG_CAT_CODE"); 

    						BigDecimal costChgAmt = (BigDecimal) results.get(0).get("COST_CHANGE_AMT");	
    						BigDecimal costChgAmJpy = (BigDecimal)results.get(0).get("COST_CHANGE_AMT_JPY");
    						BigDecimal qty = (BigDecimal) results.get(0).get("PART_QTY");	
    						BigDecimal shareRate = (BigDecimal)results.get(0).get("SHARE_RATE_PERCENT");	

    						if(costChgCatCode.trim().equalsIgnoreCase("BC")){

    							if(suppNo.equalsIgnoreCase("JN9999")){
    								endCostAmt=costChgAmJpy;
    							}else{
    								endMCCAmt=costChgAmt;
    							}
    							if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")){
    								if(!((findVariance( new BigDecimal(0.0000),endCostAmt, new BigDecimal(0.0000), endMCCAmt, qty.intValue(), shareRate))
    										.compareTo(BigDecimal.ZERO) == 0)){
    									System.out.println("variance found");
    									returnParam[0]=procSecCode;
    									returnParam[1]=suppNo;
    								}
    							} else if(StringUtils.equals(baseOrCurrentEventData, "PREVIOUS")){
    								if(!((findVariance( endCostAmt, new BigDecimal(0.0000),endMCCAmt, new BigDecimal(0.0000),  qty.intValue(), shareRate))
    										.compareTo(BigDecimal.ZERO) == 0)){
    									System.out.println("variance found");
    									returnParam[0]=procSecCode;
    									returnParam[1]=suppNo;
    								}
    							}
    						}
    	}

    	//logger.info("\n Exiting method - checkifProcSectionIsChanged() in "+CLASS_NAME);
    	return returnParam;
    }
    
    
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
    
	private BigDecimal findMCCCost(BigDecimal mccCost, int partQty, BigDecimal sharePercent){
		return ((mccCost.multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)))).setScale(4, RoundingMode.DOWN);
	}
	
	private BigDecimal findEndCost(BigDecimal endCost, int partQty, BigDecimal sharePercent, BigDecimal mccCost){
		
		/*BigDecimal partTotalCost = (endCost.multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)));
		
		BigDecimal mccTotalCost = findMCCCost(mccCost, partQty, sharePercent);*/
			//(mccCost.multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)));
		//Above code is commented as it is calculating BC and MCC separately and then adding their result. Instead we can add BC and MCC at first and then calculate the ened cost.
		BigDecimal partTotalCost = ((endCost.add(mccCost)).multiply(new BigDecimal(partQty))).multiply(sharePercent.divide(new BigDecimal(100)));
		
		//return (partTotalCost.add(mccTotalCost)).setScale(4, RoundingMode.DOWN);
		return (partTotalCost).setScale(4, RoundingMode.DOWN);
	}
    //CPT-524 end
    //INC0726363  / CPT-357 - check partial part number match for part added dropped and apply rules
    public String[] checkIfPartialPartMatchExists(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO,
    		EnterACCEventPartDetailsDTO eventPartDetails, String baseOrCurrentEventData, EnterACCSuppFEMDMTODTO femdDTO, int charMatch) {
    	//logger.info("\n Entering method - checkifProcSectionIsChanged() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
    	String[] returnParam = new String[2];
    	//CPT-449 start
    		//StringBuilder querySB;
    		
			String querySB = (ENTER_ACC_SUPP_MTO_SUMMARY_PROC_SECT_CODE_CHANGE_ENTIRE_EVENT);
/*			if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("USD")){
				querySB=querySB.replace("--SUPPLIER_CONDITION--", " EPM.SUPPLIER_NO <> 'JN9999' ");
			}else if  (enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("JPY")){ 
				querySB=querySB.replace("--SUPPLIER_CONDITION--", " EPM.SUPPLIER_NO = 'JN9999' ");
			}*/
    		//CPT-449 end
    		Map<String, Object> queryParameters = new HashMap<String, Object>();
    		if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")){
    			queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent());
        		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev());
        		log.info("charMatch==="+charMatch+" "+eventPartDetails.getM_strPartNumber());
        		queryParameters.put("partNumber", eventPartDetails.getM_strPartNumber().substring(0, charMatch)+"%");
        		//queryParameters.put("suppNumber", eventPartDetails.getM_strSupplierNumber());
        		queryParameters.put("modelCatCode", eventPartDetails.getM_strModelCatCode());
        		queryParameters.put("modelDevCode", 
        				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getBaseFrameApplication().getTargetModel()
        						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getBaseEngineApplication().getTargetModel()
        								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getBaseMissionApplication().getTargetModel()
        										: femdDTO.getBaseDifferentialApplication().getTargetModel());
        		queryParameters.put("mtcType", 
        				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getBaseFrameApplication().getType()
        						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getBaseEngineApplication().getType()
        								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getBaseMissionApplication().getType()
        										: femdDTO.getBaseDifferentialApplication().getType());
        		
        		
    		} else {
    			queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent());
        		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev());
        		queryParameters.put("partNumber", eventPartDetails.getM_strPartNumber().substring(0, charMatch)+"%");
        		//queryParameters.put("suppNumber", eventPartDetails.getM_strSupplierNumber());
        		queryParameters.put("modelCatCode", eventPartDetails.getM_strModelCatCode());
        		queryParameters.put("modelDevCode", 
        				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getCurrentFrameApplication().getTargetModel()
        						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getCurrentEngineApplication().getTargetModel()
        								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getCurrentMissionApplication().getTargetModel()
        										: femdDTO.getCurrentDifferentialApplication().getTargetModel());
        		queryParameters.put("mtcType", 
        				eventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getCurrentFrameApplication().getType()
        						: eventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getCurrentEngineApplication().getType()
        								: eventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getCurrentMissionApplication().getType()
        										: femdDTO.getCurrentDifferentialApplication().getType());
        		
    		}
    		returnParam[0]="";
    		log.info("queryParameters - "+queryParameters);
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB), queryParameters);
        	if(results!=null && !results.isEmpty()){
        		
         		returnParam[0] = (String)results.get(0).get("PROC_SECT_CODE");
        		returnParam[1] = (String)results.get(0).get("SUPPLIER_NO");
        		log.info("part number mapped with  -"+ (String)results.get(0).get("PART_NO"));
        	}
        		
    	//logger.info("\n Exiting method - checkifProcSectionIsChanged() in "+CLASS_NAME);
    	return returnParam;
    }

    //INC0726363  / CPT-357 - end
    
    
    /**
     * This method check is there is design section, Qty or share rate change for Proc Sect change
     * @param enterACCApplicationsSuppMTOSummaryDVO
     * @param previousEventPartDetails
     * @param currentEventPartDetails
     * @param baseOrCurrentEventData
     */
    public void checkQtyShareRateDesignSectChangeForHierarchy(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCEventPartDetailsDTO previousEventPartDetails, EnterACCEventPartDetailsDTO currentEventPartDetails, String baseOrCurrentEventData
    		,EnterACCSuppFEMDMTODTO femdDTO){
    	//log.info("\n Entering method - checkQtyShareRateDesignSectChangeForHierarchy() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
    	String[] returnParam = new String[3];
    	StringBuilder querySB;
    	Map<String, Object> queryParameters = new HashMap<String, Object>();
    	
		querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_CHECK_DESIGN_SECT_QTY_SHARERATE);
    	
		if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")){
			queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent());
    		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev());
    		queryParameters.put("partNumber", previousEventPartDetails.getM_strPartNumber());
    		queryParameters.put("suppNumber", previousEventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("modelCatCode", currentEventPartDetails.getM_strModelCatCode());
    		queryParameters.put("modelDevCode", 
    				currentEventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getBaseFrameApplication().getTargetModel()
    						: currentEventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getBaseEngineApplication().getTargetModel()
    								: currentEventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getBaseMissionApplication().getTargetModel()
    										: femdDTO.getBaseDifferentialApplication().getTargetModel());
    		queryParameters.put("mtcType", 
    				currentEventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getBaseFrameApplication().getType()
    						: currentEventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getBaseFrameApplication().getType()
    								: currentEventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getBaseFrameApplication().getType()
    										: femdDTO.getBaseFrameApplication().getType());
    		queryParameters.put("procSection", previousEventPartDetails.getM_strProcSectCode());
		} else {
			queryParameters.put("eventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent());
    		queryParameters.put("eventRev", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev());
    		queryParameters.put("partNumber", currentEventPartDetails.getM_strPartNumber());
    		queryParameters.put("suppNumber", currentEventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("modelCatCode", previousEventPartDetails.getM_strModelCatCode());
    		queryParameters.put("modelDevCode", 
    				previousEventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getCurrentFrameApplication().getTargetModel()
    						: previousEventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getCurrentEngineApplication().getTargetModel()
    								: previousEventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getCurrentMissionApplication().getTargetModel()
    										: femdDTO.getCurrentDifferentialApplication().getTargetModel());
    		queryParameters.put("mtcType", 
    				previousEventPartDetails.getM_strModelCatCode().equals("F") ? femdDTO.getCurrentFrameApplication().getType()
    						: previousEventPartDetails.getM_strModelCatCode().equals("E") ? femdDTO.getCurrentEngineApplication().getType()
    								: previousEventPartDetails.getM_strModelCatCode().equals("M") ? femdDTO.getCurrentMissionApplication().getType()
    										: femdDTO.getCurrentDifferentialApplication().getType());
    		queryParameters.put("procSection", currentEventPartDetails.getM_strProcSectCode());
		}
		
		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
		
		if(results!=null && !results.isEmpty()){
			if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")){
				previousEventPartDetails.setM_strPartSectionCode((String)results.get(0).get("PART_SECTION_CODE"));
				previousEventPartDetails.setM_intPartQty(((BigDecimal)results.get(0).get("PART_QTY")).intValueExact());
				previousEventPartDetails.setM_decShareRatePercent((BigDecimal)results.get(0).get("SHARE_RATE_PERCENT"));
			} else {
				currentEventPartDetails.setM_strPartSectionCode((String)results.get(0).get("PART_SECTION_CODE"));
				currentEventPartDetails.setM_intPartQty(((BigDecimal)results.get(0).get("PART_QTY")).intValueExact());
				currentEventPartDetails.setM_decShareRatePercent((BigDecimal)results.get(0).get("SHARE_RATE_PERCENT"));
			}
			
    	}
		
		//log.info("\n Exiting method - checkQtyShareRateDesignSectChangeForHierarchy() in "+CLASS_NAME);
    }
    
    /**
     * This method is used to fetch ACC data for Proc group change or Part Added/Dropped
     * @param enterACCApplicationsSuppMTOSummaryDVO
     * @param eventPartDetails
     * @param femdDTO
     * @param baseOrCurrentEventData
     * @return
     */
    public ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> fetchACCDataForProcChangePartAddedDropped(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO, 
    		EnterACCEventPartDetailsDTO eventPartDetails, EnterACCSuppFEMDMTODTO femdDTO, String baseOrCurrentEventData) {
    	log.info("\n Entering method - fetchACCDataForProcChangePartAddedDropped() in "+CLASS_NAME);
    	List<Map<String,Object>> results = null;
		StringBuilder querySB;
		EnterACCSuppSummaryACCDataDetailsDTO enterACCSuppSummaryACCDataDetailsDTO;
		ArrayList<EnterACCSuppSummaryACCDataDetailsDTO> m_lenterACCSuppSummaryACCDataDetailsDTOList = new ArrayList<EnterACCSuppSummaryACCDataDetailsDTO>();
		//CPT-382 adding condtion to pass current supplier number if current section is called
		if(StringUtils.equals(baseOrCurrentEventData, "BASE")){ 
			querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA);
		}else{	
			querySB = new StringBuilder(ENTER_ACC_SUPP_MTO_SUMMARY_PROC_CHANGE_ADDED_DROPPED_PARTS_ACC_DATA_CURRENT);
		}	
			if(!StringUtils.equals(baseOrCurrentEventData, "")){
				if(StringUtils.equals(baseOrCurrentEventData, "BASE")){
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='B'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT='C'");
				} else if(StringUtils.equals(baseOrCurrentEventData, "CURRENT_SAME")) {
					querySB.append(" AND ACC.IS_BASE_OR_CURRENT_EVENT IN ('C', 'S' ) ");
				}
			}
			
			querySB.append(" ORDER BY ACC.MODIFIED_TSTP ");
			
			Map<String, Object> queryParameters = new HashMap<String, Object>();
    		
    		queryParameters.put("baseEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent().trim());
    		queryParameters.put("baseEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev().trim()));
    		queryParameters.put("currentEventName", enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent().trim());
    		queryParameters.put("currentEventRev", new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev().trim()));
    		
    		if(StringUtils.equals(baseOrCurrentEventData, "BASE")){ 
        		queryParameters.put("currentTgtModelDevCode",femdDTO.getCurrentFrameApplication()!=null?femdDTO.getCurrentFrameApplication().getTargetModel()!=null?femdDTO.getCurrentFrameApplication().getTargetModel():"":""); 
        		queryParameters.put("currentMTCType", femdDTO.getCurrentFrameApplication()!=null?femdDTO.getCurrentFrameApplication().getType()!=null?femdDTO.getCurrentFrameApplication().getType():"":"");
        		queryParameters.put("baseTgtModelDevCode", eventPartDetails.getM_strTgtModelDevCodeFrame());
        		queryParameters.put("baseMTCType", eventPartDetails.getM_strMTCTypeFrame());
        		
        		queryParameters.put("currentTgtModelDevCodeEngine",femdDTO.getCurrentEngineApplication()!=null?femdDTO.getCurrentEngineApplication().getTargetModel()!=null?femdDTO.getCurrentEngineApplication().getTargetModel():"":""); 
        		queryParameters.put("currentMTCTypeEngine", femdDTO.getCurrentEngineApplication()!=null?femdDTO.getCurrentEngineApplication().getType()!=null?femdDTO.getCurrentEngineApplication().getType():"":"");
        		queryParameters.put("baseTgtModelDevCodeEngine", eventPartDetails.getM_strTgtModelDevCodeEngine());
        		queryParameters.put("baseMTCTypeEngine", eventPartDetails.getM_strMTCTypeEngine());
        		
        		queryParameters.put("currentTgtModelDevCodeMission",femdDTO.getCurrentMissionApplication()!=null?femdDTO.getCurrentMissionApplication().getTargetModel()!=null?femdDTO.getCurrentMissionApplication().getTargetModel():"":""); 
        		queryParameters.put("currentMTCTypeMission", femdDTO.getCurrentMissionApplication()!=null?femdDTO.getCurrentMissionApplication().getType()!=null?femdDTO.getCurrentMissionApplication().getType():"":"");
        		queryParameters.put("baseTgtModelDevCodeMission", eventPartDetails.getM_strTgtModelDevCodeMission());
        		queryParameters.put("baseMTCTypeMission", eventPartDetails.getM_strMTCTypeMission());
        		
        		queryParameters.put("currentTgtModelDevCodeDiff",femdDTO.getCurrentDifferentialApplication()!=null?femdDTO.getCurrentDifferentialApplication().getTargetModel()!=null?femdDTO.getCurrentDifferentialApplication().getTargetModel():"":""); 
        		queryParameters.put("currentMTCTypeDiff", femdDTO.getCurrentDifferentialApplication()!=null?femdDTO.getCurrentDifferentialApplication().getType()!=null?femdDTO.getCurrentDifferentialApplication().getType():"":"");
        		queryParameters.put("baseTgtModelDevCodeDiff", eventPartDetails.getM_strTgtModelDevCodeDifferential());
        		queryParameters.put("baseMTCTypeDiff", eventPartDetails.getM_strMTCTypeDifferential());
    		} else {
        		queryParameters.put("currentTgtModelDevCode",eventPartDetails.getM_strTgtModelDevCodeFrame()); 
        		queryParameters.put("currentMTCType", eventPartDetails.getM_strMTCTypeFrame());
        		queryParameters.put("baseTgtModelDevCode", femdDTO.getBaseFrameApplication()!=null?femdDTO.getBaseFrameApplication().getTargetModel()!=null?femdDTO.getBaseFrameApplication().getTargetModel():"":"");
        		queryParameters.put("baseMTCType", femdDTO.getBaseFrameApplication()!=null?femdDTO.getBaseFrameApplication().getType()!=null?femdDTO.getBaseFrameApplication().getType():"":"");
        		
        		queryParameters.put("currentTgtModelDevCodeEngine",eventPartDetails.getM_strTgtModelDevCodeEngine()); 
        		queryParameters.put("currentMTCTypeEngine",eventPartDetails.getM_strMTCTypeEngine());
        		queryParameters.put("baseTgtModelDevCodeEngine",femdDTO.getBaseEngineApplication()!=null?femdDTO.getBaseEngineApplication().getTargetModel()!=null?femdDTO.getBaseEngineApplication().getTargetModel():"":"");
        		queryParameters.put("baseMTCTypeEngine", femdDTO.getBaseEngineApplication()!=null?femdDTO.getBaseEngineApplication().getType()!=null?femdDTO.getBaseEngineApplication().getType():"":"");
        		
        		queryParameters.put("currentTgtModelDevCodeMission",eventPartDetails.getM_strTgtModelDevCodeMission()); 
        		queryParameters.put("currentMTCTypeMission", eventPartDetails.getM_strMTCTypeMission());
        		queryParameters.put("baseTgtModelDevCodeMission", femdDTO.getBaseMissionApplication()!=null?femdDTO.getBaseMissionApplication().getTargetModel()!=null? femdDTO.getBaseMissionApplication().getTargetModel():"":"");
        		queryParameters.put("baseMTCTypeMission",femdDTO.getBaseMissionApplication() !=null?femdDTO.getBaseMissionApplication().getType()!=null?femdDTO.getBaseMissionApplication().getType():"":"");
        		
        		queryParameters.put("currentTgtModelDevCodeDiff",eventPartDetails.getM_strTgtModelDevCodeDifferential()); 
        		queryParameters.put("currentMTCTypeDiff", eventPartDetails.getM_strMTCTypeDifferential());
        		queryParameters.put("baseTgtModelDevCodeDiff", femdDTO.getBaseDifferentialApplication()!=null?femdDTO.getBaseDifferentialApplication().getTargetModel()!=null?femdDTO.getBaseDifferentialApplication().getTargetModel():"":"");
        		queryParameters.put("baseMTCTypeDiff", femdDTO.getBaseDifferentialApplication()!=null?femdDTO.getBaseDifferentialApplication().getType()!=null?femdDTO.getBaseDifferentialApplication().getType():"":"");
    		}
    		
    		queryParameters.put("modelCatCode", eventPartDetails.getM_strModelCatCode());
    		queryParameters.put("plantLocCode", eventPartDetails.getM_strPlantLocCode());
    		
    		queryParameters.put("partNumberCurrent", eventPartDetails.getM_strPartNumber());
    		queryParameters.put("partNumberBase", eventPartDetails.getM_strPartNumber());
    		
    		/* Considering only base supplier in order to fetch appropriate ACC:
    		 * 1. In case user does a BOM maintenance and changes the current supplier to same as base supplier then we need to fetch that ACC too.
    		 * 2. In case user does a BOM maintenance and changes the quantity or share rate in the current event and makes it same as base event then we need to fetch that ACC too.
    		 * In both the above scenarios user should be able to view the ACC already present and take appropriate action on the screen.(Either delete, reject based on the status of the ACC.)
    		 */
    		queryParameters.put("baseSupplierNumber", eventPartDetails.getM_strSupplierNumber());
    		queryParameters.put("baseSupplierNumber", eventPartDetails.getM_strSupplierNumber());
		
    		String partColorCode = "";
    		    // Use existing object available in this method
    		    partColorCode = eventPartDetails.getM_strPartColorCode();
    		// handle null safely
    		queryParameters.put("partColorCode", 
    		    partColorCode != null ? partColorCode.trim() : "");
		
    		queryParameters.put("partSectCode",eventPartDetails.getM_strPartSectionCode());
    		queryParameters.put("procSectCode",eventPartDetails.getM_strProcSectCode());
    		//queryParameters.put("isBaseCurrent",(StringUtils.equals(baseOrCurrentEventData, "BASE") ? "B" : "C"));
    		log.info("query & parameters - "+querySB.toString()+" params - "+queryParameters);
    		results = getNamedParameterJdbcTemplateObject().queryForList(replaceSchemaNames(querySB.toString()), queryParameters);
    		
    		for(Map<String,Object> accDataObj : results){
    			enterACCSuppSummaryACCDataDetailsDTO = new EnterACCSuppSummaryACCDataDetailsDTO();
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strRuleId((String)accDataObj.get("RULE_ID"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAppCostChangeCode((String)accDataObj.get("APP_COST_CHANGE_CODE"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_decACCAmount((BigDecimal)accDataObj.get("ACC_AMOUNT"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccStatus(String.valueOf((Integer)accDataObj.get("ACC_STATUS")));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccRulePartCharMatch(((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH"))!=null?!((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim().isEmpty() ? ((String)accDataObj.get("ACC_RULE_PART_CHAR_MATCH")).trim(): "":"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strPartDistinguishingReason((String)accDataObj.get("PART_DISTINGUISHING_REASON"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strEffectiveDate(Utility.convertFromUtilDateToStr((Date)accDataObj.get("EFFECTIVE_DATE"),"MM/dd/yyyy"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedBy((String)accDataObj.get("MODIFIED_BY"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strModifiedDate(Utility.convertSqlTimestamptoStringACC((Timestamp)accDataObj.get("MODIFIED_TSTP"),"yyyy-MM-dd-HH.mm.ss"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccComments((String)accDataObj.get("ACC_COMMENTS"));
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentDesc(accDataObj.get("CODE_DESC_TEXT")!=null ? ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[0] :"");
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strAccCommentNote(accDataObj.get("CODE_DESC_TEXT")!=null && ((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@").length >1 ? 
    					((String)accDataObj.get("CODE_DESC_TEXT")).split("@_@")[1] :"");//Note to be sub stringed from CODE_DESC_TEXT to be done after reply from business on Codes table.
    			enterACCSuppSummaryACCDataDetailsDTO.setM_strBaseOrCurrentEvent((String)accDataObj.get("IS_BASE_OR_CURRENT_EVENT"));
    			m_lenterACCSuppSummaryACCDataDetailsDTOList.add(enterACCSuppSummaryACCDataDetailsDTO);
    		}
    		
    	log.info("\n Exiting method - fetchACCDataForProcChangePartAddedDropped() in "+CLASS_NAME);
    	return m_lenterACCSuppSummaryACCDataDetailsDTOList;
    }
    
    /**
	 * This method will get all active rules to be applied.
	 */
	@SuppressWarnings("rawtypes")
	public void findAllActiveRules(){
		log.info("Entering method - findAllActiveRules() in ACCBatchDAO");
		
		for(Map map: getJdbcTemplate().queryForList(replaceSchemaNames(FIND_ACTIVE_RULES))){
			AccRuleEnum.valueOf(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("RULE_ID")))))
					.setRuleId(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("RULE_ID")))));
			AccRuleEnum.valueOf(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("RULE_ID")))))
					.setRuleDescText(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("RULE_DESC_TEXT")))));
			AccRuleEnum.valueOf(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("RULE_ID")))))
					.setAppCostChangeCode(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("APP_COST_CHANGE_CODE")))));
			AccRuleEnum.valueOf(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("RULE_ID")))))
					.setStatus(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("STATUS")))).equalsIgnoreCase("ENABLED"));
		}
		
		log.info("Returning from method - findAllActiveRules() in ACCBatchDAO");
	}
	
	/**
	 * This method will get all In-House suppliers from FCCOD1 table
	 */
	@SuppressWarnings("rawtypes")
	public ArrayList<String> findInHouseSupp(){
		log.info("Entering method - findInHouseSupp() in "+ CLASS_NAME +".");
		ArrayList<String> inHouseSupp = new ArrayList<String>();
		for(Map map: getJdbcTemplate().queryForList(replaceSchemaNames(FIND_INHOUSE_SUPPLIER))){
			
			inHouseSupp.add(Utility.convertNullToBlank(Utility.trimStringValue(String.valueOf(map.get("CODE")))));
		}
		log.info("Exiting from method - findInHouseSupp() in "+ CLASS_NAME +".");
		return inHouseSupp;
	}
	
	/**
	 * This method is used to delete all the existing data in the the staging table before inserting the new processed data 
	 * @param enterACCApplicationsSuppMTOSummaryDVO
	 */
	public void deleteACC2Data(EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO){
		log.info("Entering deleteACC2Data() method in "+ CLASS_NAME +".");
		
		int deletedRows = 0;
    	String query = DELETE_ACC2_DATA; 
		String strProcGrps = "";
		for (String procGrp : getProcGroupsBetweenSelProcGrps(enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupFrom(), enterACCApplicationsSuppMTOSummaryDVO.getM_strProcGroupTo()))
		{
			strProcGrps += "'"+procGrp.trim()+"',";
		}
		String supplierCondition="";
		//If job is submitted for 'USD' then delete only those records whose whose supplier is not 'JN9999' and vice versa 
		if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency()!=null && enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("USD")){
			supplierCondition=" AND SUPPLIER_NO <> 'JN9999'";
		}else if(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency()!=null && enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrency().trim().equalsIgnoreCase("JPY")){
			supplierCondition=" AND SUPPLIER_NO = 'JN9999'";
		}
		strProcGrps=strProcGrps.substring(0, strProcGrps.length()-1);//To remove last comma
		String strMTO="";
		for(EnterACCSuppFEMDMTODTO enterACCSuppFEMDMTODTO:enterACCApplicationsSuppMTOSummaryDVO.getM_lEnterACCSuppFEMDMTODTOList()){
			strMTO=strMTO+" SELECT '"
					+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseFrameApplication()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getBaseFrameApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentFrameApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getCurrentFrameApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseEngineApplication()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getBaseEngineApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentEngineApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getCurrentEngineApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseMissionApplication()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getBaseMissionApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentMissionApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getCurrentMissionApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getBaseDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getBaseDifferentialApplication().getOption().trim():"":"")
					+"','"+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getTargetModel().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getType().trim():"":"")+"','"
					+(enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getOption()!=null?enterACCSuppFEMDMTODTO.getCurrentDifferentialApplication().getOption().trim():"":"")
			+"' FROM SYSIBM.SYSDUMMY1 UNION ALL";
		}
		strMTO=strMTO.trim().isEmpty()?"'','','','','','','','','','','','','','','','','','','','','','','',''":strMTO.substring(0, strMTO.length()-10);//To remove last comma
		log.info("ACC2 data deleted : "+strMTO+" params:"+enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent()+" "+new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev())+
				" "+enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent()+" "+new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev()));
		//Check if Jobs already exist
		deletedRows = getJdbcTemplate().update(replaceSchemaNames(query).replace("--PROC_GRPS--", strProcGrps).replace("--STR_MTO--", strMTO)+supplierCondition,
				new Object[]{enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEvent(),new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strCurrentEventRev()),
						enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEvent(),new BigDecimal(enterACCApplicationsSuppMTOSummaryDVO.getM_strBaseEventRev())});
		
		log.info("Total number of records deleted from ACC2 table: "+deletedRows );
		
		log.info("Exiting deleteACC2Data() method in "+ CLASS_NAME +".");
	}
	
	/**
     * To fetch all the proc group between selected proc groups
     * @param requestDVO
     * @return
     * @throws ApplicationException
     */
    private List<String> getProcGroupsBetweenSelProcGrps(String procGrpFrom, String procGrpTo) {

    	log.info("\n Entering getProcGroupsBetweenSelProcGrps() method in "+CLASS_NAME);
    	List<Map<String, Object>> results = null;
    	List<String> procGroupsList=new ArrayList<String>();

		//If proc groups are not selected then all proc groups between A and 99 should be fetched
		results = getJdbcTemplate().queryForList(replaceSchemaNames(GET_PROC_GROUP_BET_SEL_PROC_GRPS),
				new Object[]{procGrpFrom!=null && !procGrpFrom.trim().isEmpty()?procGrpFrom:"A",
						procGrpTo!=null && !procGrpTo.trim().isEmpty()?procGrpTo:"99"});
		if(results!=null){
			for(Map map:results){
				procGroupsList.add((String)map.get("CODE"));
			}
		}
		log.info("\n Exiting getProcGroupsBetweenSelProcGrps() method in "+CLASS_NAME);
    	return procGroupsList;
    }
	
	/**
	 * This method inserts the processed ACC data in the staging table to used on screen 
	 * @param accDataToSaveInACC2
	 */
	public void insertACC2Data(List<Object[]> accDataToSaveInACC2){
		log.info("Entering insertACC2Data() method in "+ CLASS_NAME +".");
		
		int[] row = getJdbcTemplate().batchUpdate(replaceSchemaNames(INSERT_ACC2_DATA), accDataToSaveInACC2);
		
		log.info("Total number of records inserted in ACC2 table: "+row.toString());
		
		log.info("Exiting insertACC2Data() method in "+ CLASS_NAME +".");
	}

	public void updateJobStatusInFCASD1(String status,EnterACCApplicationsSuppMTOSummaryDVO enterACCApplicationsSuppMTOSummaryDVO) {
		log.info("Entering updateJobStatusInFCASD1() method in "+ CLASS_NAME +".");
		
		int row = getJdbcTemplate().update(replaceSchemaNames(UPDATE_STATUS_IN_FCADS1), 
				new Object[] {status, enterACCApplicationsSuppMTOSummaryDVO.getSeqNo()});
		log.info("Total number of records inserted in ACC2 table: "+row);
		
		log.info("Exiting updateJobStatusInFCASD1() method in "+ CLASS_NAME +".");
	}
	
	public EmailUserDTO findUserInfoForEmailNotification(String userLogonId) {
		log.info("Entering findUserInfoForEmailNotification() method in "+ CLASS_NAME +".");
		
		List<Map<String, Object>> results = null;
		results = getJdbcTemplate().queryForList(replaceSchemaNames(GET_MAIL_ID_LOGGED_IN_USER), new Object[] {userLogonId.toUpperCase(), userLogonId.toUpperCase()});
		EmailUserDTO emailUser = null;
		
		for(Map map : results){
			emailUser = new EmailUserDTO();
			emailUser.setEmailid((String) map.get("USER_EMAIL_ID"));
			break;
		}
		
		log.info("Exiting findUserInfoForEmailNotification() method in "+ CLASS_NAME +".");
		return emailUser;
	}
	
	public AccDefinitionDto findACCJobDefinitionBySeqNo(BigDecimal seqNo) {
		List<Map<String, Object>> results = null;
		AccDefinitionDto accDefinition = null;

		results = getJdbcTemplate().queryForList(replaceSchemaNames(FETCH_ACC_DEFINITION), new Object[]{seqNo});
		if(results!=null){
			for (Map map : results) {
				accDefinition = new AccDefinitionDto();
				accDefinition.setSeqNo((BigDecimal) map.get("SEQ_NO"));
				accDefinition.setStatus((String) map.get("STATUS"));
				accDefinition.setDescText((String) map.get("DESC_TEXT"));
				accDefinition.setCreatedBy((String) map.get("CREATED_BY"));
			}
		}
		return accDefinition;
	}

}






