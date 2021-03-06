/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.bundleplugin;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.Manifest;

import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Instructions;
import aQute.bnd.osgi.Processor;
import aQute.lib.collections.ExtList;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Resource;
import org.apache.maven.shared.dependency.graph.DependencyNode;


/**
 * Generate an OSGi manifest for this project
 */
@Mojo( name = "manifest", requiresDependencyResolution = ResolutionScope.TEST,
       threadSafe = true,
       defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ManifestPlugin extends BundlePlugin
{
    /**
     * When true, generate the manifest by rebuilding the full bundle in memory
     */
    @Parameter( property = "rebuildBundle" )
    protected boolean rebuildBundle;

    @Override
    protected void execute( MavenProject project, DependencyNode dependencyGraph, Map<String, String> instructions, Properties properties, Jar[] classpath )
        throws MojoExecutionException
    {
        Analyzer analyzer;
        try
        {
            analyzer = getAnalyzer(project, dependencyGraph, instructions, properties, classpath);
        }
        catch ( FileNotFoundException e )
        {
            throw new MojoExecutionException( "Cannot find " + e.getMessage()
                + " (manifest goal must be run after compile phase)", e );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error trying to generate Manifest", e );
        }
        catch ( MojoFailureException e )
        {
            getLog().error( e.getLocalizedMessage() );
            throw new MojoExecutionException( "Error(s) found in manifest configuration", e );
        }
        catch ( Exception e )
        {
            getLog().error( "An internal error occurred", e );
            throw new MojoExecutionException( "Internal error in maven-bundle-plugin", e );
        }

        File outputFile = new File( manifestLocation, "MANIFEST.MF" );

        try
        {
            writeManifest( analyzer, outputFile, niceManifest, exportScr, scrLocation );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Error trying to write Manifest to file " + outputFile, e );
        }
        finally
        {
            try 
            {
                analyzer.close();
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Error trying to write Manifest to file " + outputFile, e );
            }
        }
    }


    public Manifest getManifest( MavenProject project, DependencyNode dependencyGraph, Jar[] classpath ) throws IOException, MojoFailureException,
        MojoExecutionException, Exception
    {
        return getManifest( project, dependencyGraph, new LinkedHashMap<String, String>(), new Properties(), classpath );
    }


    public Manifest getManifest( MavenProject project, DependencyNode dependencyGraph, Map<String, String> instructions, Properties properties, Jar[] classpath )
        throws IOException, MojoFailureException, MojoExecutionException, Exception
    {
        Analyzer analyzer = getAnalyzer(project, dependencyGraph, instructions, properties, classpath);

        Jar jar = analyzer.getJar();
        Manifest manifest = jar.getManifest();

        if (exportScr)
        {
            exportScr(analyzer, jar, scrLocation);
        }

        // cleanup...
        analyzer.close();

        return manifest;
    }
    
    private static void exportScr(Analyzer analyzer, Jar jar, File scrLocation) throws Exception {
        scrLocation.mkdirs();

        String bpHeader = analyzer.getProperty(Analyzer.SERVICE_COMPONENT);
        Parameters map = Processor.parseHeader(bpHeader, null);
        for (String root : map.keySet())
        {
            Map<String, Resource> dir = jar.getDirectories().get(root);
            File location = new File(scrLocation, root);
            if (dir == null || dir.isEmpty())
            {
                Resource resource = jar.getResource(root);
                if (resource != null)
                {
                    writeSCR(resource, location);
                }
            }
            else
            {
                for (Map.Entry<String, Resource> entry : dir.entrySet())
                {
                    String path = entry.getKey();
                    Resource resource = entry.getValue();
                    writeSCR(resource, new File(location, path));
                }
            }
        }
    }

    private static void writeSCR(Resource resource, File destination) throws Exception
    {
        destination.getParentFile().mkdirs();
        OutputStream os = new FileOutputStream(destination);
        try
        {
            resource.write(os);
        }
        finally
        {
            os.close();
        }
    }

    protected Analyzer getAnalyzer( MavenProject project, DependencyNode dependencyGraph, Jar[] classpath ) throws IOException, MojoExecutionException,
        Exception
    {
        return getAnalyzer( project, dependencyGraph, new LinkedHashMap<String, String>(), new Properties(), classpath );
    }


    protected Analyzer getAnalyzer( MavenProject project, DependencyNode dependencyGraph, Map<String, String> instructions, Properties properties, Jar[] classpath )
        throws IOException, MojoExecutionException, Exception
    {
        if ( rebuildBundle && supportedProjectTypes.contains( project.getArtifact().getType() ) )
        {
            return buildOSGiBundle( project, dependencyGraph, instructions, properties, classpath );
        }

        File file = getOutputDirectory();
        if ( file == null )
        {
            file = project.getArtifact().getFile();
        }

        if ( !file.exists() )
        {
            if ( file.equals( getOutputDirectory() ) )
            {
                file.mkdirs();
            }
            else
            {
                throw new FileNotFoundException( file.getPath() );
            }
        }

        Builder analyzer = getOSGiBuilder( project, instructions, properties, classpath );

        analyzer.setJar( file );

        // calculateExportsFromContents when we have no explicit instructions defining
        // the contents of the bundle *and* we are not analyzing the output directory,
        // otherwise fall-back to addMavenInstructions approach

        boolean isOutputDirectory = file.equals( getOutputDirectory() );

        if ( analyzer.getProperty( Analyzer.EXPORT_PACKAGE ) == null
            && analyzer.getProperty( Analyzer.EXPORT_CONTENTS ) == null
            && analyzer.getProperty( Analyzer.PRIVATE_PACKAGE ) == null && !isOutputDirectory )
        {
            String export = calculateExportsFromContents( analyzer.getJar() );
            analyzer.setProperty( Analyzer.EXPORT_PACKAGE, export );
        }

        addMavenInstructions( project, dependencyGraph, analyzer );

        // if we spot Embed-Dependency and the bundle is "target/classes", assume we need to rebuild
        if ( analyzer.getProperty( DependencyEmbedder.EMBED_DEPENDENCY ) != null && isOutputDirectory )
        {
            analyzer.build();
        }
        else
        {
            analyzer.mergeManifest( analyzer.getJar().getManifest() );
            analyzer.getJar().setManifest( analyzer.calcManifest() );
        }

        mergeMavenManifest( project, dependencyGraph, analyzer );

        boolean hasErrors = reportErrors( "Manifest " + project.getArtifact(), analyzer );
        if ( hasErrors )
        {
            String failok = analyzer.getProperty( "-failok" );
            if ( null == failok || "false".equalsIgnoreCase( failok ) )
            {
                throw new MojoFailureException( "Error(s) found in manifest configuration" );
            }
        }

        Jar jar = analyzer.getJar();

        if ( unpackBundle )
        {
            File outputFile = getOutputDirectory();
            for ( Entry<String, Resource> entry : jar.getResources().entrySet() )
            {
                File entryFile = new File( outputFile, entry.getKey() );
                if ( !entryFile.exists() || entry.getValue().lastModified() == 0 )
                {
                    entryFile.getParentFile().mkdirs();
                    OutputStream os = new FileOutputStream( entryFile );
                    entry.getValue().write( os );
                    os.close();
                }
            }
        }

        return analyzer;
    }


    public static void writeManifest( Analyzer analyzer, File outputFile, boolean niceManifest,
            boolean exportScr, File scrLocation ) throws Exception
    {
        Properties properties = analyzer.getProperties();
        Jar jar = analyzer.getJar();
        Manifest manifest = jar.getManifest();
        if ( outputFile.exists() && properties.containsKey( "Merge-Headers" ) )
        {
            Manifest analyzerManifest = manifest;
            manifest = new Manifest();
            InputStream inputStream = new FileInputStream( outputFile );
            try
            {
                manifest.read( inputStream );
            }
            finally
            {
                inputStream.close();
            }
            Instructions instructions = new Instructions( ExtList.from( analyzer.getProperty("Merge-Headers") ) );
            mergeManifest( instructions, manifest, analyzerManifest );
        }
        else
        {
            File parentFile = outputFile.getParentFile();
            parentFile.mkdirs();
        }
        writeManifest( manifest, outputFile, niceManifest );
        
        if (exportScr)
        {
            exportScr(analyzer, jar, scrLocation);            
        }
    }


    public static void writeManifest( Manifest manifest, File outputFile, boolean niceManifest ) throws IOException
    {
        outputFile.getParentFile().mkdirs();

        FileOutputStream os;
        os = new FileOutputStream( outputFile );
        try
        {
            ManifestWriter.outputManifest( manifest, os, niceManifest );
        }
        finally
        {
            try
            {
                os.close();
            }
            catch ( IOException e )
            {
                // nothing we can do here
            }
        }
    }


    /*
     * Patched version of bnd's Analyzer.calculateExportsFromContents
     */
    public static String calculateExportsFromContents( Jar bundle )
    {
        String ddel = "";
        StringBuffer sb = new StringBuffer();
        Map<String, Map<String, Resource>> map = bundle.getDirectories();
        for ( Iterator<Entry<String, Map<String, Resource>>> i = map.entrySet().iterator(); i.hasNext(); )
        {
            //----------------------------------------------------
            // should also ignore directories with no resources
            //----------------------------------------------------
            Entry<String, Map<String, Resource>> entry = i.next();
            if ( entry.getValue() == null || entry.getValue().isEmpty() )
                continue;
            //----------------------------------------------------
            String directory = entry.getKey();
            if ( directory.equals( "META-INF" ) || directory.startsWith( "META-INF/" ) )
                continue;
            if ( directory.equals( "OSGI-OPT" ) || directory.startsWith( "OSGI-OPT/" ) )
                continue;
            if ( directory.equals( "/" ) )
                continue;

            if ( directory.endsWith( "/" ) )
                directory = directory.substring( 0, directory.length() - 1 );

            directory = directory.replace( '/', '.' );
            sb.append( ddel );
            sb.append( directory );
            ddel = ",";
        }
        return sb.toString();
    }
}
