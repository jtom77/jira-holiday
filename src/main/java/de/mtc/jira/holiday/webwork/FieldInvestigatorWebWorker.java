package de.mtc.jira.holiday.webwork;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.OrderableField;
import com.atlassian.jira.issue.fields.layout.field.EditableFieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenLayoutItem;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.issue.operation.ScreenableIssueOperation;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.web.action.JiraWebActionSupport;

public class FieldInvestigatorWebWorker extends JiraWebActionSupport {

	private static final long serialVersionUID = 1L;
		
	private String projectKey = "ISF";
	
	public void setProjectKey(String projectKey) {
		this.projectKey = projectKey;
	}
	
	public Project getProject() {
		return 	ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
	}
	
	public IssueTypeScreenScheme getIssueTypeScreenScheme() {
		return ComponentAccessor.getIssueTypeScreenSchemeManager().getIssueTypeScreenScheme(getProject());
	}
	
	public List<ScreenableIssueOperation> getIssueOperations() {
		return Arrays.asList(IssueOperations.CREATE_ISSUE_OPERATION,IssueOperations.EDIT_ISSUE_OPERATION,IssueOperations.VIEW_ISSUE_OPERATION);
	}
	
	public Collection<IssueType> getIssueTypes() {
		return ComponentAccessor.getConstantsManager().getAllIssueTypeObjects();
	}

	public List<EditableFieldLayout> getEditableFieldLayouts() {
		return ComponentAccessor.getFieldLayoutManager().getEditableFieldLayouts();
	}
	
	public List<String> getIssueTypesAsStrings() {
		return getIssueTypes().stream().map(t -> t.getId()).collect(Collectors.toList());		
	}

	
	public void doit() {
		IssueTypeScreenScheme issueTypeScreenScheme = getIssueTypeScreenScheme();
		for(IssueType issueType : getIssueTypes()) {
			FieldScreenScheme fieldScreenScheme = issueTypeScreenScheme.getEffectiveFieldScreenScheme(issueType);
			for(ScreenableIssueOperation issueOperation : getIssueOperations()) {
				FieldScreen fieldScreen = fieldScreenScheme.getFieldScreen(issueOperation);
				for(FieldScreenLayoutItem fieldScreenLayoutItem : fieldScreen.getTab(0).getFieldScreenLayoutItems()) {
					OrderableField field = fieldScreenLayoutItem.getOrderableField();
					field.getName();
				}
			}
		}
	}
	
	
	public void doit2() {
		FieldLayoutManager fieldLayoutManager = ComponentAccessor.getFieldLayoutManager();
		List<String> issueTypes = getIssueTypes().stream().map(t -> t.getId()).collect(Collectors.toList());
		for(EditableFieldLayout editableFieldLayout : fieldLayoutManager.getEditableFieldLayouts()) {
			System.out.println(editableFieldLayout.getName());
			System.out.println(editableFieldLayout.getHiddenFields(getProject(), issueTypes));
			System.out.println(editableFieldLayout.getHiddenFields(getProject(), issueTypes));
		}
	}
	

	@Override
	public String doDefault() throws Exception {
		return INPUT;
	}
	
	@Override
	protected String doExecute() throws Exception {
		return SUCCESS;
	}
}
