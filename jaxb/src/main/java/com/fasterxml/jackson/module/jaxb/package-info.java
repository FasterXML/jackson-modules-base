/**
 * Package that contains support for using JAXB annotations for
 * configuring Jackson data-binding aspects.
 *<p>
 * Usage is by registering {@link com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule}:
 *<pre>
 *  ObjectMapper mapper = new ObjectMapper();
 *  mapper.registerModule(new JaxbAnnotationModule());
 *</pre>
 */
package com.fasterxml.jackson.module.jaxb;
