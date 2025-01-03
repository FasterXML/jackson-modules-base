/**
 * Package info can be used to add "package annotations", so here we are...
 */
@javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters({
  @javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter(
    type = javax.xml.namespace.QName.class,
    value = tools.jackson.module.jaxb.introspect.JaxbAnnotationIntrospectorTest.QNameAdapter.class
  )
})
package tools.jackson.module.jaxb.misc;

