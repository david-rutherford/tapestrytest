package com.reged.tapestrytest.services;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.apache.tapestry5.ioc.services.ClasspathURLConverter;

public class ClasspathURLConverterJBoss7Dot1 implements ClasspathURLConverter
{
    private static Logger logger = org.slf4j.LoggerFactory.getLogger(ClasspathURLConverterJBoss7Dot1.class);

    public URL convert(URL url)
    {
        // If the URL is a "vfs" URL (JBoss 7.1 uses a Virtual File System)...

        if (url != null && url.getProtocol().startsWith("vfs"))
        {
            // Ask the VFS what the physical URL is...

            try
            {
                String urlString = url.toString();
                        
                // If the virtual URL involves a JAR file, 
                // we have to figure out its physical URL ourselves because
                // in JBoss 7.1 the JAR files exploded into the VFS are empty 
                // (see https://issues.jboss.org/browse/JBAS-8786).
                // Our workaround is that they are available, unexploded, 
                // within the otherwise exploded WAR file.

                if (urlString.contains(".jar") && !Files.isDirectory(Paths.get(urlString.substring(5)))) {
                                        
                    // An example URL: "vfs:/devel/jboss-7.1.0.Final/server/default/deploy/myapp.ear/myapp.war/WEB-INF/lib/tapestry-core-5.3.3.jar/org/apache/tapestry5/corelib/components/"
                    // Break the URL into its WAR part, the JAR part, 
                    // and the Java package part.
                                        
                    int warPartEnd = urlString.indexOf(".war") + 4;
                    String warPart = urlString.substring(0, warPartEnd);
                    int jarPartEnd = urlString.indexOf(".jar") + 4;
                    String jarPart = urlString.substring(warPartEnd, jarPartEnd);
                    String packagePart = urlString.substring(jarPartEnd);

                    // Ask the VFS where the exploded WAR is.

                    URL warURL = new URL(warPart);
                    URLConnection warConnection = warURL.openConnection();
                    Object jBossVirtualWarDir = warConnection.getContent();
                    // Use reflection so that we don't need JBoss in the classpath at compile time.
                    File physicalWarDir = (File) invoke(jBossVirtualWarDir, "getPhysicalFile");
                    String physicalWarDirStr = physicalWarDir.toURI().toString();

                    // Return a "jar:" URL constructed from the parts
                    // eg. "jar:file:/devel/jboss-7.1.0.Final/server/default/tmp/vfs/automount40a6ed1db5eabeab/myapp.war-43e2c3dfa858f4d2//WEB-INF/lib/tapestry-core-5.3.3.jar!/org/apache/tapestry5/corelib/components/".

                    String actualJarPath = "jar:" + physicalWarDirStr + jarPart + "!" + packagePart;
                    return new URL(actualJarPath);
                }
                                
                // Otherwise, ask the VFS what the physical URL is...
                                
                else {

                    URLConnection connection = url.openConnection();
                    Object jBossVirtualFile = connection.getContent();
                    logger.debug(jBossVirtualFile.getClass().getName()+": "+url+" class:" + jBossVirtualFile.getClass().getName());
                    // Use reflection so that we don't need JBoss in the classpath at compile time.
                    URL physicalFileURL = null;
                    if (jBossVirtualFile instanceof FileInputStream) {
                    	physicalFileURL = new URL("file", url.getHost(), url.getPort(), url.getFile());
                    } else if (jBossVirtualFile.getClass().getName().endsWith("URLImageSource")) {
                    	physicalFileURL = url;                    	
                	} else {                    	
	                    File physicalFile = (File) invoke(jBossVirtualFile, "getPhysicalFile");
	                    physicalFileURL = physicalFile.toURI().toURL();
                    }
                    logger.debug(physicalFileURL.toString());
                    return physicalFileURL;
                }

            }
            catch (Exception e)
            {
                Throwable cause = e.getCause();
                if (cause != null) {
                	logger.error(cause.toString());
                } else {
                	logger.error(e.toString());
                }
            }
        }

        return url;
    }

    private Object invoke(Object target, String getter) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Class<?> type = target.getClass();
        Method method;
        try
        {
            method = type.getMethod(getter);
        }
        catch (NoSuchMethodException e)
        {
            method = type.getDeclaredMethod(getter);
            method.setAccessible(true);
        }
        return method.invoke(target);
    }

}