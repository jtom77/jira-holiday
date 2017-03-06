package de.mtc.jira.holiday.webwork;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.JiraContextNode;
import com.atlassian.jira.issue.context.ProjectContext;
import com.atlassian.jira.issue.customfields.CustomFieldType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfig;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.mail.Email;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.atlassian.jira.workflow.WorkflowUtil;
import com.atlassian.mail.queue.MailQueue;
import com.atlassian.mail.queue.SingleMailQueueItem;
import com.atlassian.mail.server.SMTPMailServer;
import com.opensymphony.workflow.FactoryException;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;

import de.mtc.jira.holiday.WorkflowHelper;

public class ProjectCreationWebWorker extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ProjectCreationWebWorker.class);

	public final static String TEXT_SINGLE_LINE = "com.atlassian.jira.plugin.system.customfieldtypes:textfield";
	public final static String DATE_PICKER = "com.atlassian.jira.plugin.system.customfieldtypes:datepicker";
	public final static String SELECT = "com.atlassian.jira.plugin.system.customfieldtypes:select";
	public final static String READ_ONLY = "com.atlassian.jira.plugin.system.customfieldtypes:readonlyfield";

	private List<CustomField> customFields;
	private List<FieldScreen> fieldScreens;
	private String error;

	public List<FieldScreen> getFieldScreens() {
		return fieldScreens;
	}

	public List<CustomField> getCustomFields() {
		return customFields;
	}

	public String getError() {
		return error;
	}

	@Override
	protected String doExecute() throws Exception {
		log.debug("Executing main method");
		printWorkflow();
		try {
			createAllFields();
		} catch (Exception e) {
			log.debug("Unable to create fields", e);
			error = e.getMessage();
			return ERROR;
		}
		customFields = ComponentAccessor.getCustomFieldManager().getCustomFieldObjects();
		fieldScreens = new ArrayList<>(ComponentAccessor.getFieldScreenManager().getFieldScreens());
		return SUCCESS;
	}

	@Override
	public String doDefault() throws Exception {
		log.debug("Executing default method");
		return INPUT;
	}

	public void createAllFields() throws GenericEntityException {

		String description = "Automatically created for holiday MTC project";
		for (String propKey : new String[] { "cf.start_date", "cf.end_date" }) {
			String name = WorkflowHelper.getProperty(propKey);
			createCustomField(name, description, DATE_PICKER);
		}

		for (String propKey : new String[] { "cf.annual_leave", "cf.residual_days" }) {
			String name = WorkflowHelper.getProperty(propKey);
			createCustomField(name, description, READ_ONLY);
		}

		String name = WorkflowHelper.getProperty("cf.holiday_type");
		CustomField cf = createCustomField(name, description, SELECT);
		if (cf != null) {
			List<FieldConfigScheme> schemes = cf.getConfigurationSchemes();
			if (schemes != null && !schemes.isEmpty()) {
				FieldConfigScheme sc = schemes.get(0);
				@SuppressWarnings("rawtypes")
				Map configs = sc.getConfigsByConfig();
				if (configs != null && !configs.isEmpty()) {
					FieldConfig config = (FieldConfig) configs.keySet().iterator().next();
					OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
					List<Option> options = optionsManager.createOptions(config, null, 1L,
							Arrays.asList("Halbe Tage", "Ganze Tage"));
					log.info("Added Options {} to {}. All options {}", options, cf, optionsManager.getOptions(config));
				}
			}
		}
	}

	public CustomField createCustomField(String name, String description, String type) throws GenericEntityException {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		CustomFieldType<?, ?> fieldType = cfm.getCustomFieldType(type);
		List<IssueType> issueTypes = new ArrayList<>(ComponentAccessor.getConstantsManager().getAllIssueTypeObjects());
		List<JiraContextNode> jiraContextNodes = ComponentAccessor.getProjectManager().getProjectObjects().stream()
				.map(project -> new ProjectContext(project.getId())).collect(Collectors.toList());

		Collection<CustomField> existing = cfm.getCustomFieldObjectsByName(name);
		if (existing != null && !existing.isEmpty()) {
			log.debug("Custom Field \"" + name + "\" already exists");
			return null;
		}
		CustomField field = cfm.createCustomField(name, description, fieldType, null, jiraContextNodes, issueTypes);
		log.debug("## Created custom Field " + field.getName() + ", " + field.getId() + ", " + field.getNameKey() + " "
				+ field.getClass());
		log.debug("Created Custom field. Name: %s, Id: %s, NameKey: %s, Class: %s", field.getName(), field.getId(),
				field.getNameKey(), field.getClass());
		addToFieldScreen(field);
		return field;
	}

	public void deleteRedundantFields() {
		CustomFieldManager cfm = ComponentAccessor.getCustomFieldManager();
		Map<String, CustomField> tempMap = new HashMap<>();
		for (CustomField cf : cfm.getCustomFieldObjects()) {
			String name = cf.getName();
			if (tempMap.get(name) == null) {
				tempMap.put(name, cf);
			} else {
				try {
					cfm.removeCustomField(cf);
				} catch (RemoveException e) {
					log.debug("Couldn't remove field " + cf.getName(), e);
				}
				log.debug("Successfully removed field " + cf.getName());
			}
		}
	}

	private void sendEmail() {
		log.debug("sending mail");
		SMTPMailServer mailServer = ComponentAccessor.getMailServerManager().getDefaultSMTPMailServer();
		Email mail = new Email("thomas.epp@mtc.berlin");
		mail.setFrom(mailServer.getDefaultFrom());
		mail.setSubject("Jira Test Mail");
		mail.setBody("This is a jira test mail");
		SingleMailQueueItem smqi = new SingleMailQueueItem(mail);
		MailQueue mailQueue = ComponentAccessor.getMailQueue();
		mailQueue.addItem(smqi);

		log.debug("Mail Server: {}, MailQueue {}", mailServer.getDefaultFrom(), mailQueue.getQueue());
		log.debug("Item being sent {}", mailQueue.getItemBeingSent());
	}

	public void createUser() throws CreateException, PermissionException {
		// ComponentAccessor.getUserManager().createUser(new
		// UserDetails("teamlead","Team Lead"));
		// ComponentAccessor.getUserManager().createUser(new
		// UserDetails("manager","Manager"));
		// ComponentAccessor.getUserManager().createUser(new
		// UserDetails("teamlead","Team Lead"));
	}

	private void addToFieldScreen(CustomField cf) {
		FieldScreenManager fieldScreenManager = ComponentAccessor.getFieldScreenManager();
		for (FieldScreen screen : fieldScreenManager.getFieldScreens()) {
			if (screen.getName().startsWith(WorkflowHelper.getProperty("holiday.project.key"))) {
				log.info("Adding Customfield {} to screen {}", cf.getName(), screen.getName());
				screen.getTab(0).addFieldScreenLayoutItem(cf.getId());
			}
		}
	}

	private void printWorkflow() {

		InputStream in = this.getClass().getClassLoader().getResourceAsStream("workflow.xml");
		String xml = null;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
			xml = reader.lines().collect(Collectors.joining("\n"));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		WorkflowDescriptor workflowDescriptor = null;
		try {
			workflowDescriptor = WorkflowUtil.convertXMLtoWorkflowDescriptor(xml);
		} catch (FactoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		StatusManager statusManager = ComponentAccessor.getComponent(StatusManager.class);
		List<StepDescriptor> stepDescriptor = workflowDescriptor.getSteps();
		Collection<Status> givenStatuses = statusManager.getStatuses();

		Map<String, String> actionNames = new HashMap<>();

		for (StepDescriptor sd : stepDescriptor) {

			Status given = null;
			for (Status status : givenStatuses) {
				if (status.getName().equals(sd.getName())) {
					given = status;
					break;
				}
			}
			if (given == null) {
				Status status = statusManager.createStatus(sd.getName(), sd.getName(), "/images/icons/pluginIcon.png");
				Map newStatus = new HashMap();
				newStatus.put("jira.status.id", status.getId());
				sd.setMetaAttributes(newStatus);
				given = status;
			}

			log.debug("Status: {} Id: {}", given.getName(), given.getId());
			actionNames.put(given.getName(), given.getId());
		}

		try {

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(false);
			dbf.setNamespaceAware(true);
			dbf.setFeature("http://xml.org/sax/features/namespaces", false);
			dbf.setFeature("http://xml.org/sax/features/validation", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(this.getClass().getClassLoader().getResourceAsStream("workflow.xml"));
			NodeList nl = doc.getElementsByTagName("step");

			for (int i = 0; i < nl.getLength(); i++) {
				Element step = (Element) nl.item(i);
				String name = step.getAttribute("name");
				if (name == null) {
					continue;
				}				
				// jira.status.id
				String id = actionNames.get(name);
				if (id == null) {
					continue;
				}
				
				NodeList metas = step.getElementsByTagName("meta");
				for(int j=0; j<metas.getLength(); j++) {
					Element meta = (Element) metas.item(j);
					if("jira.status.id".equals(meta.getAttribute("name"))) {
						meta.setNodeValue(id);
					}
				}
			}

			// write the content into xml file
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(new File("C:\\Users\\EMJVK\\temp\\workflow.xml"));

			transformer.transform(source, result);

		} catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
