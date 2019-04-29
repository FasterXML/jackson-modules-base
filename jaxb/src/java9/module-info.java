module com.fasterxml.jackson.module.jaxb {
	requires com.fasterxml.jackson.databind;
	requires java.activation;
	requires java.xml;
	requires java.xml.bind;
	requires java.desktop;

	exports com.fasterxml.jackson.module.jaxb;

	provides com.fasterxml.jackson.databind.Module with com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
}