/**
 * nfms4redd Portal Interface - http://nfms4redd.org/
 *
 * (C) 2012, FAO Forestry Department (http://www.fao.org/forestry/)
 *
 * This application is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package org.fao.unredd.statsCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.fao.unredd.process.ProcessExecutionException;
import org.fao.unredd.process.ProcessRunner;

/**
 * Wraps a text file, allows performing replacements on $variables and executes
 * it
 * 
 * @author fergonco
 */
public class Script {

	private String content;
	private VelocityContext context;

	public Script(InputStream scriptStream) throws IOException {
		content = IOUtils.toString(scriptStream);
		context = new VelocityContext();
	}

	public void setParameter(String paramName, Object value) {
		context.put(paramName, value);
	}

	public void run() throws ProcessExecutionException {
		VelocityEngine engine = new VelocityEngine();
		engine.setProperty("resource.loader", "class");
		engine.setProperty("class.resource.loader.class",
				"org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		engine.init();

		StringWriter finalContent = new StringWriter();
		if (!Velocity.evaluate(context, finalContent, "stats.sh", content)) {
			throw new ProcessExecutionException(
					"Cannot execute the indicator shell script");
		}
		new ProcessRunner("bash", "-c", finalContent.toString()).run();
	}
}
