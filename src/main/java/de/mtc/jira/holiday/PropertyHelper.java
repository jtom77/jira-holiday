package de.mtc.jira.holiday;

import static com.opensymphony.module.propertyset.PropertySet.BOOLEAN;
import static com.opensymphony.module.propertyset.PropertySet.DATE;
import static com.opensymphony.module.propertyset.PropertySet.DOUBLE;
import static com.opensymphony.module.propertyset.PropertySet.INT;
import static com.opensymphony.module.propertyset.PropertySet.LONG;
import static com.opensymphony.module.propertyset.PropertySet.STRING;

import java.util.Date;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.opensymphony.module.propertyset.PropertySet;

public class PropertyHelper {

	private PropertySet props;

	public PropertyHelper(ApplicationUser user) {
		this.props = ComponentAccessor.getUserPropertyManager().getPropertySet(user);
	}

	public Double getDouble(String key) {
		return Double.valueOf(get(key).toString());
	}

	public Object get(String key) {
		int type = props.getType(key);
		switch (type) {
		case BOOLEAN:
			return new Boolean(props.getBoolean(key));
		case DATE:
			return props.getDate(key);
		case DOUBLE:
			return props.getDouble(key);
		case INT:
			return props.getInt(key);
		case LONG:
			return props.getLong(key);
		case STRING:
			return props.getString(key);
		default:
			return props.getObject(key);

		}
	}

	public PropertySet getProps() {
		return props;
	}

	public boolean exists(String key) {
		return props.exists(key);
	}
	
	public void set(String key, Object o) {
		int type = props.getType(key);
		switch (type) {
		case BOOLEAN:
			props.setBoolean(key, Boolean.valueOf(o.toString()));
			break;
		case DATE:
			props.setDate(key, new Date(o.toString()));
			break;
		case DOUBLE:
			props.setDouble(key, Double.valueOf(o.toString()));
			break;
		case INT:
			props.setInt(key, Integer.valueOf(o.toString()));
			break;
		case LONG:
			props.setLong(key, Long.valueOf(o.toString()));
			break;
		case STRING:
			props.setString(key, o.toString());
			break;
		default:
			props.setObject(key, o);

		}
	}
}
