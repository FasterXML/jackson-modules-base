import com.fasterxml.jackson.databind.Module;

module com.fasterxml.jackson.module.paranamer {
	requires com.fasterxml.jackson.databind;
	requires paranamer;
	requires com.fasterxml.jackson.core;

	provides Module with com.fasterxml.jackson.module.paranamer.ParanamerModule;
}
