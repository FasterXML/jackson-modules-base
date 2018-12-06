import com.fasterxml.jackson.databind.Module;

module com.fasterxml.jackson.module.jaxb {
	requires transitive com.fasterxml.jackson.databind;

	requires java.xml.bind;

	requires static java.desktop;

	provides Module with com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
}
