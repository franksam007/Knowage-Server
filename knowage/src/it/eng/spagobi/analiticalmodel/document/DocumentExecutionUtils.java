package it.eng.spagobi.analiticalmodel.document;

import it.eng.spago.base.SourceBean;
import it.eng.spago.base.SourceBeanAttribute;
import it.eng.spago.error.EMFUserError;
import it.eng.spago.security.IEngUserProfile;
import it.eng.spagobi.analiticalmodel.document.bo.BIObject;
import it.eng.spagobi.analiticalmodel.document.handlers.DocumentParameters;
import it.eng.spagobi.analiticalmodel.document.handlers.DocumentUrlManager;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.BIObjectParameter;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.ObjParuse;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.Parameter;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.bo.ParameterUse;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.dao.IObjParuseDAO;
import it.eng.spagobi.behaviouralmodel.analyticaldriver.dao.IParameterUseDAO;
import it.eng.spagobi.behaviouralmodel.lov.bo.ILovDetail;
import it.eng.spagobi.behaviouralmodel.lov.bo.LovDetailFactory;
import it.eng.spagobi.behaviouralmodel.lov.bo.LovResultHandler;
import it.eng.spagobi.behaviouralmodel.lov.bo.ModalitiesValue;
import it.eng.spagobi.behaviouralmodel.lov.bo.QueryDetail;
import it.eng.spagobi.commons.bo.UserProfile;
import it.eng.spagobi.commons.dao.DAOFactory;
import it.eng.spagobi.commons.serializer.JSONStoreFeedTransformer;
import it.eng.spagobi.commons.utilities.AuditLogUtilities;
import it.eng.spagobi.user.UserProfileManager;
import it.eng.spagobi.utilities.assertion.Assert;
import it.eng.spagobi.utilities.cache.CacheInterface;
import it.eng.spagobi.utilities.cache.CacheSingleton;
import it.eng.spagobi.utilities.exceptions.SpagoBIRuntimeException;
import it.eng.spagobi.utilities.exceptions.SpagoBIServiceException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DocumentExecutionUtils {
	public static final String PARAMETERS = "PARAMETERS";
	public static final String SERVICE_NAME = "GET_URL_FOR_EXECUTION_ACTION";
	public static String MODE_SIMPLE = "simple";
	public static String MODE_COMPLETE = "complete";
	public static String START = "start";
	public static String LIMIT = "limit";

	public static ILovDetail getLovDetail(BIObjectParameter parameter) {
		Parameter par = parameter.getParameter();
		ModalitiesValue lov = par.getModalityValue();
		String lovProv = lov.getLovProvider();
		ILovDetail lovProvDet = null;
		try {
			lovProvDet = LovDetailFactory.getLovFromXML(lovProv);
		} catch (Exception e) {
			throw new SpagoBIRuntimeException("Impossible to get lov detail associated to input BIObjectParameter", e);
		}
		return lovProvDet;
	}

	public static List<DocumentParameters> getParameters(BIObject obj, String executionRole, Locale locale, String modality) {
		List<DocumentParameters> parametersForExecution = new ArrayList<DocumentParameters>();
		BIObject document = new BIObject();
		try {
			document = DAOFactory.getBIObjectDAO().loadBIObjectForExecutionByIdAndRole(obj.getId(), executionRole);
		} catch (EMFUserError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<BIObjectParameter> parameters = document.getBiObjectParameters();
		if (parameters != null && parameters.size() > 0) {
			Iterator<BIObjectParameter> it = parameters.iterator();
			while (it.hasNext()) {
				BIObjectParameter parameter = it.next();
				parametersForExecution.add(new DocumentParameters(parameter, executionRole, locale, document));
			}
		}
		return parametersForExecution;
	}

	public static JSONObject handleNormalExecution(UserProfile profile, BIObject obj, HttpServletRequest req, String env, String role, String modality,
			String parametersJson, Locale locale) { // isFromCross,

		JSONObject response = new JSONObject();
		HashMap<String, String> logParam = new HashMap<String, String>();
		logParam.put("NAME", obj.getName());
		logParam.put("ENGINE", obj.getEngine().getName());
		logParam.put("PARAMS", parametersJson); // this.getAttributeAsString(PARAMETERS)
		DocumentUrlManager documentUrlManager = new DocumentUrlManager(profile, locale);
		try {
			List errors = null;
			JSONObject executionInstanceJSON = null;
			try {
				executionInstanceJSON = new JSONObject(parametersJson);
			} catch (JSONException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			documentUrlManager.refreshParametersValues(executionInstanceJSON, false, obj);
			try {
				errors = documentUrlManager.getParametersErrors(obj, role);
			} catch (Exception e) {
				throw new SpagoBIServiceException(SERVICE_NAME, "Cannot evaluate errors on parameters validation", e);
			}
			try {
				errors = documentUrlManager.getParametersErrors(obj, role);
			} catch (Exception e) {
				throw new SpagoBIServiceException(SERVICE_NAME, "Cannot evaluate errors on parameters validation", e);
			}
			// ERRORS
			// if (errors != null && errors.size() > 0) {
			// there are errors on parameters validation, send errors' descriptions to the client
			JSONArray errorsArray = new JSONArray();
			Iterator errorsIt = errors.iterator();
			while (errorsIt.hasNext()) {
				EMFUserError error = (EMFUserError) errorsIt.next();
				errorsArray.put(error.getDescription());
			}
			try {
				response.put("errors", errorsArray);
			} catch (JSONException e) {
				try {
					AuditLogUtilities.updateAudit(req, profile, "DOCUMENT.GET_URL", logParam, "ERR");
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				throw new SpagoBIServiceException(SERVICE_NAME, "Cannot serialize errors to the client", e);
			}
			// }else {
			// URL
			// there are no errors, we can proceed, so calculate the execution url and send it back to the client
			String url = documentUrlManager.getExecutionUrl(obj, modality, role);
			// url += "&isFromCross=" + (isFromCross == true ? "true" : "false");
			// adds information about the environment
			if (env == null) {
				env = "DOCBROWSER";
			}
			url += "&SBI_ENVIRONMENT=" + env;
			try {
				response.put("url", url);
			} catch (JSONException e) {
				try {
					AuditLogUtilities.updateAudit(req, profile, "DOCUMENT.GET_URL", logParam, "KO");
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				try {
					AuditLogUtilities.updateAudit(req, profile, "DOCUMENT.GET_URL", logParam, "ERR");
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				throw new SpagoBIServiceException(SERVICE_NAME, "Cannot serialize the url [" + url + "] to the client", e);
			}

			AuditLogUtilities.updateAudit(req, profile, "DOCUMENT.GET_URL", logParam, "OK");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response;
	}

	public static String handleNormalExecutionUrl(UserProfile profile, BIObject obj, HttpServletRequest req, String env, String role, String modality,
			String parametersJson, Locale locale) { // isFromCross,

		HashMap<String, String> logParam = new HashMap<String, String>();
		logParam.put("NAME", obj.getName());
		logParam.put("ENGINE", obj.getEngine().getName());
		logParam.put("PARAMS", parametersJson); // this.getAttributeAsString(PARAMETERS)
		DocumentUrlManager documentUrlManager = new DocumentUrlManager(profile, locale);
		String url = "";
		try {
			JSONObject executionInstanceJSON = null;
			try {
				executionInstanceJSON = new JSONObject(parametersJson);
			} catch (JSONException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
			documentUrlManager.refreshParametersValues(executionInstanceJSON, false, obj);

			// URL
			// there are no errors, we can proceed, so calculate the execution url and send it back to the client
			url = documentUrlManager.getExecutionUrl(obj, modality, role);
			// url += "&isFromCross=" + (isFromCross == true ? "true" : "false");
			// adds information about the environment
			if (env == null) {
				env = "DOCBROWSER";
			}
			url += "&SBI_ENVIRONMENT=" + env;

			AuditLogUtilities.updateAudit(req, profile, "DOCUMENT.GET_URL", logParam, "OK");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return url;
	}

	public static List handleNormalExecutionError(UserProfile profile, BIObject obj, HttpServletRequest req, String env, String role, String modality,
			String parametersJson, Locale locale) { // isFromCross,

		HashMap<String, String> logParam = new HashMap<String, String>();
		logParam.put("NAME", obj.getName());
		logParam.put("ENGINE", obj.getEngine().getName());
		logParam.put("PARAMS", parametersJson); // this.getAttributeAsString(PARAMETERS)
		DocumentUrlManager documentUrlManager = new DocumentUrlManager(profile, locale);
		List errors = null;
		try {

			try {
				errors = documentUrlManager.getParametersErrors(obj, role);
			} catch (Exception e) {
				throw new SpagoBIServiceException(SERVICE_NAME, "Cannot evaluate errors on parameters validation", e);
			}
			try {
				errors = documentUrlManager.getParametersErrors(obj, role);
			} catch (Exception e) {
				throw new SpagoBIServiceException(SERVICE_NAME, "Cannot evaluate errors on parameters validation", e);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return errors;
	}

	public static ArrayList<HashMap<String, Object>> getLovDefaultValues(String executionRole, BIObject biObject, BIObjectParameter objParameter,
			HttpServletRequest req) {
		ArrayList<HashMap<String, Object>> defaultValues = new ArrayList<HashMap<String, Object>>();
		String lovResult = null;
		ILovDetail lovProvDet = null;
		List rows = null;

		List<ObjParuse> biParameterExecDependencies = null;
		try {
			// ArrayList<HashMap<String, Object>> defaultValues = new ArrayList<HashMap<String,Object>>();

			lovProvDet = DocumentExecutionUtils.getLovDetail(objParameter);

			IEngUserProfile profile = UserProfileManager.getProfile();

			// LovResultCacheManager executionCacheManager = new LovResultCacheManager();

			biParameterExecDependencies = DocumentExecutionUtils.getBiObjectDependencies(executionRole, objParameter);

			lovResult = DocumentExecutionUtils.getLovResult(profile, lovProvDet, biParameterExecDependencies, biObject, req, true);

			LovResultHandler lovResultHandler = new LovResultHandler(lovResult);

			rows = lovResultHandler.getRows();

			JSONObject valuesJSON = DocumentExecutionUtils.buildJSONForLOV(lovProvDet, rows, MODE_SIMPLE);

			int valuesLength = valuesJSON.getInt("results");
			JSONArray values = valuesJSON.getJSONArray("root");

			for (int i = 0; i < valuesLength; i++) {
				JSONObject item = values.getJSONObject(i);

				HashMap<String, Object> itemAsMap = new HashMap<String, Object>();
				itemAsMap.put("value", item.get("value"));
				itemAsMap.put("label", item.get("label"));
				itemAsMap.put("description", item.get("description"));

				defaultValues.add(itemAsMap);
			}

			Assert.assertNotNull(lovResult, "Impossible to get parameter's values");

			return defaultValues;

		} catch (Exception e) {
			throw new SpagoBIServiceException("Impossible to get parameter's values", e);
		}
	}

	public static List<ObjParuse> getBiObjectDependencies(String executionRole, BIObjectParameter biobjParameter) {
		List<ObjParuse> biParameterExecDependencies = new ArrayList<ObjParuse>();
		try {
			IParameterUseDAO parusedao = DAOFactory.getParameterUseDAO();
			ParameterUse biParameterExecModality = parusedao.loadByParameterIdandRole(biobjParameter.getParID(), executionRole);
			IObjParuseDAO objParuseDAO = DAOFactory.getObjParuseDAO();
			biParameterExecDependencies.addAll(objParuseDAO.loadObjParuse(biobjParameter.getId(), biParameterExecModality.getUseID()));
		} catch (Exception e) {
			throw new SpagoBIRuntimeException("Impossible to get dependencies", e);
		}
		return biParameterExecDependencies;
	}

	public static String getLovResult(IEngUserProfile profile, ILovDetail lovDefinition, List<ObjParuse> dependencies, BIObject biObject,
			HttpServletRequest req, boolean retrieveIfNotcached) throws Exception {

		String lovResult = null;

		if (lovDefinition instanceof QueryDetail) {
			// queries are cached
			String cacheKey = getCacheKey(profile, lovDefinition, dependencies, biObject);

			CacheInterface cache = CacheSingleton.getInstance();

			if (cache.contains(cacheKey)) {
				// lov provider is present, so read the DATA in cache
				lovResult = cache.get(cacheKey);
			} else if (retrieveIfNotcached) {
				lovResult = lovDefinition.getLovResult(profile, dependencies, biObject.getBiObjectParameters(), req.getLocale());
				// insert the data in cache
				if (lovResult != null)
					cache.put(cacheKey, lovResult);
			}
		} else {
			// scrips, fixed list and java classes are not cached, and returned without considering retrieveIfNotcached input
			lovResult = lovDefinition.getLovResult(profile, dependencies, biObject.getBiObjectParameters(), req.getLocale());
		}

		return lovResult;
	}

	/**
	 * This method finds out the cache to be used for lov's result cache. This key is composed mainly by the user identifier and the lov definition. Note that,
	 * in case when the lov is a query and there is correlation, the executed statement if different from the original query (since correlation expression is
	 * injected inside SQL query using in-line view construct), therefore we should consider the modified query.
	 *
	 * @param profile
	 *            The user profile
	 * @param lovDefinition
	 *            The lov original definition
	 * @param dependencies
	 *            The dependencies to be considered (if any)
	 * @param biObject
	 *            The document object
	 * @return The key to be used in cache
	 */
	private static String getCacheKey(IEngUserProfile profile, ILovDetail lovDefinition, List<ObjParuse> dependencies, BIObject biObject) {
		String toReturn = null;
		String userID = (String) ((UserProfile) profile).getUserId();
		if (lovDefinition instanceof QueryDetail) {
			QueryDetail queryDetail = (QueryDetail) lovDefinition;
			QueryDetail clone = queryDetail.clone();
			clone.setQueryDefinition(queryDetail.getWrappedStatement(dependencies, biObject.getBiObjectParameters()));
			toReturn = userID + ";" + clone.toXML();
		} else {
			toReturn = userID + ";" + lovDefinition.toXML();
		}
		return toReturn;
	}

	public static JSONObject buildJSONForLOV(ILovDetail lovProvDet, List rows, String mode) {
		String valueColumn;
		String descriptionColumn;
		JSONObject valuesJSON;
		Integer start;
		Integer limit;
		String displayColumn;

		// START building JSON object to be returned
		try {
			JSONArray valuesDataJSON = new JSONArray();

			valueColumn = lovProvDet.getValueColumnName();
			displayColumn = lovProvDet.getDescriptionColumnName();
			descriptionColumn = displayColumn;

			// start = getAttributeAsInteger(START);
			// limit = getAttributeAsInteger(LIMIT);

			int lb = 0;
			int ub = rows.size();

			for (int q = lb; q < ub; q++) {
				SourceBean row = (SourceBean) rows.get(q);
				JSONObject valueJSON = new JSONObject();

				if (MODE_COMPLETE.equalsIgnoreCase(mode)) {
					List columns = row.getContainedAttributes();
					for (int i = 0; i < columns.size(); i++) {
						SourceBeanAttribute attribute = (SourceBeanAttribute) columns.get(i);
						valueJSON.put(attribute.getKey().toUpperCase(), attribute.getValue());
					}
				} else {
					String value = (String) row.getAttribute(valueColumn);
					String description = (String) row.getAttribute(descriptionColumn);
					valueJSON.put("value", value);
					valueJSON.put("label", description);
					valueJSON.put("description", description);
				}

				valuesDataJSON.put(valueJSON);
			}

			String[] visiblecolumns;

			if (MODE_COMPLETE.equalsIgnoreCase(mode)) {
				visiblecolumns = (String[]) lovProvDet.getVisibleColumnNames().toArray(new String[0]);
				for (int j = 0; j < visiblecolumns.length; j++) {
					visiblecolumns[j] = visiblecolumns[j].toUpperCase();
				}
			} else {

				valueColumn = "value";
				displayColumn = "label";
				descriptionColumn = "description";

				visiblecolumns = new String[] { "value", "label", "description" };
			}

			valuesJSON = (JSONObject) JSONStoreFeedTransformer.getInstance().transform(valuesDataJSON, valueColumn.toUpperCase(), displayColumn.toUpperCase(),
					descriptionColumn.toUpperCase(), visiblecolumns, new Integer(rows.size()));
			return valuesJSON;
		} catch (Exception e) {
			throw new SpagoBIServiceException("Impossible to serialize response", e);
		}
	}

}
