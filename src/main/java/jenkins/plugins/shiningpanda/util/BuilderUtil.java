/*
 * ShiningPanda plug-in for Jenkins
 * Copyright (C) 2011 ShiningPanda S.A.S.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package jenkins.plugins.shiningpanda.util;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixRun;
import hudson.matrix.MatrixProject;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;
import jenkins.plugins.shiningpanda.Messages;
import jenkins.plugins.shiningpanda.command.Command;
import jenkins.plugins.shiningpanda.interpreters.Python;
import jenkins.plugins.shiningpanda.interpreters.Virtualenv;
import jenkins.plugins.shiningpanda.matrix.PythonAxis;
import jenkins.plugins.shiningpanda.tools.PythonInstallation;
import jenkins.plugins.shiningpanda.workspace.Workspace;

public class BuilderUtil
{

    /**
     * Get the last configuration date of this build.
     * 
     * @param build
     *            The build
     * @return The last configuration date
     */
    public static long lastConfigure(AbstractBuild<?, ?> build)
    {
        // Get the last modification date of the build configuration
        long lastJobConfigure = build.getParent().getConfigFile().getFile().lastModified();
        // Get the last modification of the global configuration file
        long lastJenkinsConfigure = new File(Jenkins.getInstance().getRootDir(), "config.xml").lastModified();
        // Get the newer of the two
        return Math.max(lastJobConfigure, lastJenkinsConfigure);
    }

    /**
     * Check if this build is for a matrix project.
     * 
     * @param build
     *            The build
     * @return true if this is for a matrix project, else false
     */
    public static boolean isMatrix(AbstractBuild<?, ?> build)
    {
        return build instanceof MatrixRun;
    }

    /**
     * Check if this is a matrix project.
     * 
     * @param jobType
     *            The project
     * @return true if this is a matrix project, else false
     */
    public static boolean isMatrix(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType)
    {
        return jobType.equals(MatrixProject.class);
    }

    /**
     * Get the consolidated environment for the provided build.
     * 
     * @param build
     *            The build
     * @param listener
     *            The build listener
     * @return The consolidated environment
     * @throws IOException
     * @throws InterruptedException
     */
    public static EnvVars getEnvironment(AbstractBuild<?, ?> build, BuildListener listener) throws IOException,
            InterruptedException
    {
        // Get the base environment
        EnvVars environment = build.getEnvironment(listener);
        // Add build variables, for instance if user defined a text axis
        environment.overrideAll(build.getBuildVariables());
        // Check if define some nasty variables
        for (String key : EnvVarsUtil.getPythonHomeKeys())
            // Check if key is contained
            if (environment.containsKey(key))
            {
                // Log the error
                listener.fatalError(Messages.BuilderUtil_PythonHomeKeyFound(key));
                // Notify to do not continue the build
                return null;
            }
        // Return the consolidated environment
        return environment;
    }

    /**
     * Get the PYTHON installation.
     * 
     * @param build
     *            The build
     * @param listener
     *            The listener
     * @param environment
     *            The environment
     * @param name
     *            The name
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static PythonInstallation getInstallation(AbstractBuild<?, ?> build, BuildListener listener, EnvVars environment,
            String name) throws IOException, InterruptedException
    {
        // Check if this is a matrix build
        if (isMatrix(build))
        {
            // Check if the environment contains a PYTHON axis key
            if (!environment.containsKey(PythonAxis.KEY))
            {
                // If not, log
                listener.fatalError(Messages.BuilderUtil_PythonAxis_Required());
                // Return null to stop the build
                return null;
            }
            // Get the name from the environment
            name = environment.get(PythonAxis.KEY);
        }
        // Check if the name exists
        if (name == null)
        {
            // Log the error
            listener.fatalError(Messages.BuilderUtil_Installation_NameNotFound());
            // Do not continue the build
            return null;
        }
        // Expand the HOME folder with these variables
        PythonInstallation installation = PythonInstallation.fromName(name);
        // Check if found an installation
        if (installation == null)
        {
            // Log
            listener.fatalError(Messages.BuilderUtil_Installation_NotFound(name));
            // Failed to find the installation, do not continue
            return null;
        }
        // Get the installation for this build
        return installation.forBuild(listener, environment);
    }

    /**
     * Get an interpreter.
     * 
     * @param launcher
     *            The launcher
     * @param listener
     *            The listener
     * @param home
     *            The home of the interpreter
     * @return The interpreter if exists, else null
     * @throws IOException
     * @throws InterruptedException
     */
    public static Python getInterpreter(Launcher launcher, BuildListener listener, String home) throws IOException,
            InterruptedException
    {
        // Get an interpreter given its home
        Python interpreter = Python.fromHome(new FilePath(launcher.getChannel(), home));
        // Check if found an interpreter and if this interpreter is valid
        if (interpreter == null || !interpreter.isValid())
        {
            // Log
            listener.fatalError(Messages.BuilderUtil_Interpreter_Invalid(interpreter == null ? home : interpreter.getHome()
                    .getRemote()));
            // Invalid
            return null;
        }
        // Check if has white space in its home path
        if (StringUtil.hasWhitespace(interpreter.getHome().getRemote()))
        {
            // Log
            listener.fatalError(Messages.BuilderUtil_Interpreter_WhitespaceNotAllowed(interpreter == null ? home : interpreter
                    .getHome().getRemote()));
            // Invalid
            return null;
        }
        // This is a valid interpreter
        return interpreter;
    }

    /**
     * Get a VIRTUALENV from its home folder.
     * 
     * @param listener
     *            The listener
     * @param home
     *            The home folder
     * @return The VIRTUALENV
     * @throws IOException
     * @throws InterruptedException
     */
    public static Virtualenv getVirtualenv(BuildListener listener, FilePath home) throws IOException, InterruptedException
    {
        // Create the VIRTUAL environment
        Virtualenv virtualenv = new Virtualenv(home);
        // Check if has white space in its home path
        if (StringUtil.hasWhitespace(virtualenv.getHome().getRemote()))
        {
            // Log
            listener.fatalError(Messages.BuilderUtil_Interpreter_WhitespaceNotAllowed(virtualenv.getHome().getRemote()));
            // Invalid
            return null;
        }
        // Return the VIRTUALENV
        return virtualenv;
    }

    /**
     * Launch a command.
     * 
     * @param launcher
     *            The launcher
     * @param listener
     *            The build listener
     * @param environment
     *            The environment
     * @param workspace
     *            The workspace
     * @param interpreter
     *            The interpreter
     * @param command
     *            The command to execute
     * @param ignoreExitCode
     *            Is the exit code ignored?
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public static boolean launch(Launcher launcher, BuildListener listener, EnvVars environment, Workspace workspace,
            Python interpreter, String command, boolean ignoreExitCode) throws IOException, InterruptedException
    {
        // Set the interpreter environment
        environment.overrideAll(interpreter.getEnvironment());
        // Launch the script
        return Command.get(workspace.isUnix(), command, ignoreExitCode).launch(launcher, listener, environment,
                workspace.getHome());
    }

    /**
     * Get the first available interpreter on the executor.
     * 
     * @param launcher
     *            The launcher
     * @param listener
     *            The build listener
     * @param environment
     *            The environment
     * @return The first available interpreter
     * @throws IOException
     * @throws InterruptedException
     */
    public static Python getInterpreter(Launcher launcher, BuildListener listener, EnvVars environment) throws IOException,
            InterruptedException
    {
        // Get the list of existing interpreter
        List<Python> interpreters = getInterpreters(launcher, listener, environment);
        // Check if at least one found
        if (!interpreters.isEmpty())
            // Return the first one
            return interpreters.get(0);
        // Failed to found one
        listener.fatalError(Messages.BuilderUtil_NoInterpreterFound());
        // Return null
        return null;
    }

    /**
     * Get the list of the valid interpreter on an executor.
     * 
     * @param launcher
     *            The launcher
     * @param listener
     *            The build listener
     * @param environment
     *            The environment
     * @return The list of available interpreter
     * @throws IOException
     * @throws InterruptedException
     */
    public static List<Python> getInterpreters(Launcher launcher, BuildListener listener, EnvVars environment)
            throws IOException, InterruptedException
    {
        // Create the interpreter list
        List<Python> interpreters = new ArrayList<Python>();
        // Go threw all PYTHON installations
        for (PythonInstallation installation : PythonInstallation.list())
        {
            // Convert for the build
            installation = installation.forBuild(listener, environment);
            // Get an interpreter given its home
            Python interpreter = Python.fromHome(new FilePath(launcher.getChannel(), installation.getHome()));
            // Check if exists, is valid and has no whitespace in its home
            if (interpreter != null && interpreter.isValid() && !StringUtil.hasWhitespace(interpreter.getHome().getRemote()))
                // Add the interpreter
                interpreters.add(interpreter);
        }
        // Return the list of interpreters
        return interpreters;
    }
}
