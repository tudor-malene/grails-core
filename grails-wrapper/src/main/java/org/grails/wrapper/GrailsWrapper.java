package org.grails.wrapper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class GrailsWrapper {
    
    public static void main(final String[] args) throws Exception{
        final ResourceBundle applicationBundle = ResourceBundle.getBundle("application");
        final ResourceBundle wrapperBundle = ResourceBundle.getBundle("grails-wrapper");
        final String grailsVersion = applicationBundle.getString("app.grails.version");
        final File grailsCacheDir =  new File(System.getProperty("user.home") + "/.grails/");
        final File grailsVersionDir = new File(grailsCacheDir, grailsVersion);
        final File wrapperDir = new File(grailsVersionDir, "wrapper");
        String distUrl = wrapperBundle.getString("wrapper.dist.url");
        if(distUrl == null) {
            distUrl = "http://dist.springframework.org.s3.amazonaws.com/release/GRAILS/";
        }
        if(!distUrl.endsWith("/")) {
            distUrl += "/";
        }
        final String src = distUrl + "grails-" + grailsVersion + ".zip";
        final URI uri = new URI(src);
        
        final File file = new File(wrapperDir, "download.zip");
        new RemoteFileHelper().retrieve(uri, file);
        final File installDir = new File(wrapperDir, "install");
        if(!installDir.exists()) {
            extract(file, installDir);
        }
        final File grailsHome = new File(installDir, "grails-" + grailsVersion);
        
        System.setProperty("grails.home", grailsHome.getAbsolutePath());
        
        final List<String> newArgsList = new ArrayList<String>();
        for(int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if("--main".equals(arg) && i < args.length - 1) {
                // skip --main and the following argument
                i++;
            } else if("--conf".equals(arg) && i < args.length - 1) {
                newArgsList.add(arg);
                final File groovyStarterConf = new File(grailsHome, "conf/groovy-starter.conf");
                newArgsList.add(groovyStarterConf.getAbsolutePath());
                i++;
            } else {
                newArgsList.add(arg);
            }
        }
        
        final String[] newArgsArray = newArgsList.toArray(new String[0]);
        final URL[] urls = new URL[2];
        urls[0] = new File(grailsHome, "dist/grails-bootstrap-" + grailsVersion + ".jar").toURI().toURL();
        File[] groovyJarCandidates = new File(grailsHome, "lib/org.codehaus.groovy/groovy-all/jars/").listFiles(new FilenameFilter() {
            public boolean accept(final File dir, final String name) {
                return name.startsWith("groovy-all-") && name.endsWith(".jar");
            }
        });
        urls[1] = groovyJarCandidates[0].toURI().toURL();
        final URLClassLoader urlClassLoader = new URLClassLoader(urls);
        final Class<?> loadClass = urlClassLoader.loadClass("org.codehaus.groovy.grails.cli.support.GrailsStarter");
        final Method mainMethod = loadClass.getMethod("main", String[].class);
        
        mainMethod.invoke(null, new Object[]{newArgsArray});
    }
    
    public static void extract(final File zip, final File dest) throws IOException {
        System.out.println("Extracting " + zip.getAbsolutePath() + " to " + dest.getAbsolutePath());
        Enumeration<?> entries;
        final ZipFile zipFile = new ZipFile(zip);

        entries = zipFile.entries();

        while (entries.hasMoreElements()) {
            final ZipEntry entry = (ZipEntry) entries.nextElement();

            if (entry.isDirectory()) {
                (new File(dest, entry.getName())).mkdirs();
                continue;
            }

            copy(zipFile.getInputStream(entry),
                    new BufferedOutputStream(new FileOutputStream(new File(dest, entry.getName()))));
        }
        zipFile.close();
    }

    public static void copy(final InputStream in, final OutputStream out) throws IOException {
        final byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }
}
