import com.fasterxml.jackson.databind.Module;

module com.fasterxml.jackson.module.osgi {

	exports com.fasterxml.jackson.module.osgi;

	requires org.osgi.core;

	requires transitive com.fasterxml.jackson.core;
	requires transitive com.fasterxml.jackson.databind;

	provides Module with com.fasterxml.jackson.module.osgi.OsgiJacksonModule;
}
