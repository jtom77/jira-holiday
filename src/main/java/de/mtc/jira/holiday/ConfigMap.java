package de.mtc.jira.holiday;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMap {

	private static final Logger log = LoggerFactory.getLogger(ConfigMap.class);
	private static final Properties properties;
	private static final Map<String, String> map = new HashMap<>();

	public final static String CF_START_DATE = "cf.start_date";
	public final static String CF_END_DATE = "cf.end_date";
	public final static String CF_TYPE = "cf.holiday_type";
	public final static String CF_OPTION_HALF = "cf.holiday_type.whole";
	public final static String CF_OPTION_WHOLE = "cf.holiday_type.half";
	public final static String CF_ANNUAL_LEAVE = "cf.annual_leave";
	public final static String CF_RESIDUAL_DAYS = "cf.residual_days";
	public final static String PROP_ANNUAL_LEAVE = "prop.annual_leave";
	public final static String PROP_DAYS_OFF = "prop.days_off";

	private final static boolean local = true;

	static {
		properties = new Properties();
		try {
			properties.load(ConfigMap.class.getClassLoader().getResourceAsStream("jira-holiday.properties"));
		} catch (IOException e) {
			log.error("FATAL: Failed to load properties", e);
		}

		// String baseUrl =
		// ComponentAccessor.getComponent(JiraBaseUrls.class).baseUrl();
		// local = !baseUrl.contains("mtc.berlin");

		log.info("Initializing config map for {}", local ? "local" : "prod");

		for (Entry<Object, Object> entry : properties.entrySet()) {

			String key = entry.getKey().toString();
			String val = entry.getValue().toString();

			if (local) {
				if (key.startsWith("prod.")) {
					continue;
				} else if (key.startsWith("local.")) {
					key = key.substring("local.".length());
				}
				log.info("{}={}", key, val);
				map.put(key, val);
			} else {
				if (key.startsWith("local.")) {
					continue;
				} else if (key.startsWith("prod.")) {
					key = key.substring("prod.".length());
				}
				log.info("{}={}", key, val);
				map.put(key, val);
			}
		}
	}

	public static boolean isLocal() {
		return local;
	}

	public static String get(String key) {
		return get(key, null);
	}

	public static String get(String key, Map<String, String> replacements) {
		String value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Unknown property key " + key);
		}
		if (replacements != null) {
			value = processTemplate(value, replacements);
		}
		return value;
	}

	public static String processTemplate(String template, Map<String, String> replacements) {
		String result = template;
		for (String key : replacements.keySet()) {
			result = result.replace("{" + key + "}", replacements.get(key));
		}
		return result;
	}

	public static void debug() {
		for (String key : map.keySet()) {
			System.out.println(key + " ==> " + map.get(key));
		}
	}
}
