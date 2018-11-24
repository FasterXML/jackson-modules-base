import com.fasterxml.jackson.databind.Module;

module com.fasterxml.jackson.module.afterburner {
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires java.logging;

	requires static org.objectweb.asm;

	provides Module with com.fasterxml.jackson.module.afterburner.AfterburnerModule;
}
