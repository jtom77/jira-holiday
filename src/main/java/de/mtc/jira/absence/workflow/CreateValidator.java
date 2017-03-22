package de.mtc.jira.absence.workflow;

import java.util.Map;

import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.issue.Issue;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.Validator;
import com.opensymphony.workflow.WorkflowException;

import de.mtc.jira.absence.Absence;
import de.mtc.jira.absence.JiraValidationException;
import de.mtc.jira.absence.Vacation;

@Scanned
public class CreateValidator implements Validator {

	private final static Logger log = LoggerFactory.getLogger(CreateValidator.class);

	@SuppressWarnings("rawtypes")
	@Override
	public void validate(Map transientVars, Map args, PropertySet ps) throws InvalidInputException, WorkflowException {
		
		try {
			Issue issue = (Issue) transientVars.get("issue");
			Log.debug("Validate Creation of issue " + issue);
			
			// Note: Field checks are already perfomed by the constructors
			
			Absence absence = Absence.newInstance(issue);
			if (absence instanceof Vacation) {
				absence.getSupervisor();
				double vacationDaysSpent = absence.getVacationDaysOfThisYear();
				double numberOfWorkingDays = absence.getNumberOfWorkingDays();
				Vacation vacation = (Vacation) absence;
				double annualLeave = vacation.getAnnualLeave();
				if (vacation.getAnnualLeave() - vacation.getVacationDaysOfThisYear() < vacation
						.getNumberOfWorkingDays()) {
					StringBuilder message = new StringBuilder();
					message.append("Nicht genügend Urlaubstage für dieses Jahr.");
					message.append("\nUrlaubstage dieses Jahr: " + vacationDaysSpent);
					message.append("\nRestliche Urlaubstage: " + (annualLeave - vacationDaysSpent));
					message.append("\nBeantragt: " + numberOfWorkingDays);
					throw new InvalidInputException(message.toString());
				}
			}
			// check
			absence.validate();
			// absence.getHumanResourcesManager();
		} catch (JiraValidationException e) {
			log.error("Validation failed due to an exception: ", e);
			throw new InvalidInputException("An Exception occured while validating this issue: " + e.getMessage());
		}
	}
}
