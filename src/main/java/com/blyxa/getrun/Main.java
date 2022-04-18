package com.blyxa.getrun;

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.retrieve.RetrieveOptions;
import org.apache.ivy.core.retrieve.RetrieveReport;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.util.DefaultMessageLogger;
import org.apache.ivy.util.Message;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Main entry point to getrun.
 *
 *
 * ie.
 *      ./gradlew clean build
 *      java -jar build/libs/getrun-1.0.0.jar io.github.blyxa rotala 0.0.1-SNAPSHOT io.github.blyxa.rotala.ExampleMain
 *
 */
public class Main {

    private static final File BASEDIR = new File("artifacts");

    private static IvySettings buildIvySettings(){
        IvySettings ivySettings = new IvySettings();

        IBiblioResolver mavenLocalResolver = new IBiblioResolver();
        mavenLocalResolver.setM2compatible(true);
        mavenLocalResolver.setUsepoms(true);
        mavenLocalResolver.setRoot("file://"+System.getProperty("user.home")+"/.m2/repository");
        mavenLocalResolver.setName("mavenLocal");

        IBiblioResolver centralResolver = new IBiblioResolver();
        centralResolver.setM2compatible(true);
        centralResolver.setUsepoms(true);
        centralResolver.setName("central");

        IBiblioResolver sonaTypeSnapshotResolver = new IBiblioResolver();
        sonaTypeSnapshotResolver.setM2compatible(true);
        sonaTypeSnapshotResolver.setUsepoms(true);
        sonaTypeSnapshotResolver.setRoot("https://s01.oss.sonatype.org/content/repositories/snapshots");
        sonaTypeSnapshotResolver.setName("sonaTypeSnapshots");

        ChainResolver chainResolver = new ChainResolver();
        chainResolver.add(mavenLocalResolver);
        chainResolver.add(centralResolver);
        chainResolver.add(sonaTypeSnapshotResolver);

        ivySettings.addResolver(chainResolver);
        ivySettings.setDefaultResolver(chainResolver.getName());
        ivySettings.setBaseDir(BASEDIR);
        //ivySettings.setSettingsVariables(new File("ivysettings.xml"))
        return ivySettings;
    }
    public static void main(String[] main) throws Exception{
        if(main==null || main.length!= 4){
            System.out.println("invalid cmd");
            System.out.println("ie. java -jar getrun.jar <group> <artifact> <version> <mainClass>");
            System.exit(1);
            return;
        }

        String groupId = main[0];
        String artifactId = main[1];
        String version = main[2];
        String mainClass = main[3];

        Collection<File> files = downloadDeps(groupId, artifactId, version);
        String classPath = files.stream()
                .filter(f->f.getName().endsWith(".jar"))
                .map(f->BASEDIR.getName()+"/"+f.getName())
                .collect(Collectors.joining(":"));

        ProcessBuilder builder = new ProcessBuilder();
        builder.command("java","-cp",classPath,mainClass);

        Process process = builder.start();
        long pid = process.pid();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try{
                    Runtime.getRuntime().exec("kill " + pid).waitFor();
                    process.waitFor();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
        StreamGobbler streamGobblerErr = new StreamGobbler(process.getErrorStream(), System.out::println);
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        Executors.newSingleThreadExecutor().submit(streamGobblerErr);
        process.waitFor();
    }

    private static Collection<File> downloadDeps(String groupId,String artifactId,String version) throws Exception{
        if(!new File(BASEDIR.getName()).exists()){
            Ivy ivy = Ivy.newInstance(buildIvySettings());
            ivy.getLoggerEngine().pushLogger(new DefaultMessageLogger(Message.MSG_INFO));
            ModuleRevisionId mId = ModuleRevisionId.newInstance(groupId, artifactId, version);
            DefaultModuleDescriptor md = DefaultModuleDescriptor.newDefaultInstance(mId);
            ModuleRevisionId callerRid = ModuleRevisionId.newInstance(groupId,artifactId+"-caller",version);
            ResolveOptions ro = new ResolveOptions();
            ro.setConfs(new String[]{"default"});
            DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(md, callerRid, false, false, true);
            md.addDependency(dd);
            ResolveReport resolveReport = ivy.resolve(mId,ro,true);

            if(resolveReport.hasError()){
                resolveReport.getAllProblemMessages().forEach(System.out::println);
            }else{
                ModuleDescriptor md2= resolveReport.getModuleDescriptor();
                ModuleRevisionId mRID = md2.getModuleRevisionId();
                RetrieveOptions retrieveOptions = new RetrieveOptions();

                String pattern = "[organization]-[module]-[type]-[artifact]-[revision].jar";
                retrieveOptions.setDestIvyPattern(pattern);
                retrieveOptions.setDestArtifactPattern(pattern);
                RetrieveReport packagesRetrieved = ivy.retrieve(mRID, retrieveOptions);
                return packagesRetrieved.getRetrievedFiles();
            }
            throw new Exception("Dependencies not found");
        }
        else{
            System.out.println("artifacts folder detected... skipping download.");
            return Arrays.stream(new File(BASEDIR.getName()).listFiles())
                    .filter(f->f.getName().endsWith(".jar"))
                    .collect(Collectors.toList());
        }

    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }
}
