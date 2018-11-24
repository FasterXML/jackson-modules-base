import com.fasterxml.jackson.databind.Module;

module com.fasterxml.jackson.module.mrbean {
	requires transitive com.fasterxml.jackson.databind;
	requires org.objectweb.asm;

	provides Module with com.fasterxml.jackson.module.mrbean.MrBeanModule;
}
