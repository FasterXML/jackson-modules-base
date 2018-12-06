import com.fasterxml.jackson.databind.Module;

module com.fasterxml.jackson.module.paranamer {

	requires com.fasterxml.jackson.databind;
	requires paranamer;

	provides Module with com.fasterxml.jackson.module.paranamer.ParanamerModule;
}
