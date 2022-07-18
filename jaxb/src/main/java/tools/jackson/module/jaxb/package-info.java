/**
 * Package that contains support for using JAXB annotations for
 * configuring Jackson data-binding aspects.
 *<p>
 * Usage is by registering {@link tools.jackson.module.jaxb.JaxbAnnotationModule}:
 *<pre>
 *  ObjectMapper mapper = new ObjectMapper();
 *  mapper.registerModule(new JaxbAnnotationModule());
 *</pre>
 */
package tools.jackson.module.jaxb;
