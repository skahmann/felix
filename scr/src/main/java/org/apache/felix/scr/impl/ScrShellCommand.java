/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.scr.impl;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import org.apache.felix.shell.Command;

/**
 * The <code>ScrShellCommand</code> implements command shell access for the
 * legacy Apache Felix Shell. The actual implementation of the commands is
 * found in the {@link ScrCommand} class.
 */
class ScrShellCommand implements Command
{

    private static final String HELP_CMD = "help";
    private static final String LIST_CMD = "list";
    private static final String INFO_CMD = "info";
    private static final String ENABLE_CMD = "enable";
    private static final String DISABLE_CMD = "disable";
    private static final String CONFIG_CMD = "config";

    private final ScrCommand scrCommand;

    ScrShellCommand(final ScrCommand scrCommand)
    {
        this.scrCommand = scrCommand;
    }

    public String getName()
    {
        return "scr";
    }

    public String getUsage()
    {
        return "scr help";
    }

    public String getShortDescription()
    {
        return "Declarative Services Runtime";
    }

    public void execute(String commandLine, PrintStream out, PrintStream err)
    {
        // Parse the commandLine to get the OBR command.
        StringTokenizer st = new StringTokenizer(commandLine);
        // Ignore the invoking command.
        st.nextToken();
        // Try to get the OBR command, default is HELP command.
        String command = HELP_CMD;
        try
        {
            command = st.nextToken();
        }
        catch (Exception ex)
        {
            // Ignore.
        }

        // Perform the specified command.
        if ((command == null) || (command.equals(HELP_CMD)))
        {
            help(out, st);
        }
        else
        {
            String arg = (st.hasMoreTokens()) ? st.nextToken() : null;
            PrintWriter pw = new PrintWriter(out);
            try
            {
                if (command.equals(LIST_CMD))
                {
                    scrCommand.list(arg, pw);
                }
                else if (command.equals(INFO_CMD))
                {
                    scrCommand.info(arg, pw);
                }
                else if (command.equals(ENABLE_CMD))
                {
                    scrCommand.change(arg, pw, true);
                }
                else if (command.equals(DISABLE_CMD))
                {
                    scrCommand.change(arg, pw, false);
                }
                else if (command.equals(CONFIG_CMD))
                {
                    scrCommand.config(pw);
                }
                else
                {
                    err.println("Unknown command: " + command);
                }
            }
            catch ( IllegalArgumentException e )
            {
                System.err.println(e.getMessage());
            }
        }
    }

    private void help(PrintStream out, StringTokenizer st)
    {
        String command = HELP_CMD;
        if (st.hasMoreTokens())
        {
            command = st.nextToken();
        }
        if (command.equals(LIST_CMD))
        {
            out.println("");
            out.println("scr " + LIST_CMD + " [ <bundleId> ]");
            out.println("");
            out.println("This command lists registered components. If a bundle ID is\n"
                + "added, only the components of the selected bundles are listed.");
            out.println("");
        }
        else if (command.equals(INFO_CMD))
        {
            out.println("");
            out.println("scr " + INFO_CMD + " <componentId>");
            out.println("");
            out.println("This command dumps information of the component whose\n"
                + "component ID is given as command argument.");
            out.println("");
        }
        else if (command.equals(ENABLE_CMD))
        {
            out.println("");
            out.println("scr " + ENABLE_CMD + " <componentId>");
            out.println("");
            out.println("This command enables the component whose component ID\n" + "is given as command argument.");
            out.println("");
        }
        else if (command.equals(DISABLE_CMD))
        {
            out.println("");
            out.println("scr " + DISABLE_CMD + " <componentId>");
            out.println("");
            out.println("This command disables the component whose component ID\n" + "is given as command argument.");
            out.println("");
        }
        else if (command.equals(CONFIG_CMD))
        {
            out.println("");
            out.println("scr " + CONFIG_CMD);
            out.println("");
            out.println("This command lists the current SCR configuration.");
            out.println("");
        }
        else
        {
            out.println("scr " + HELP_CMD + " [" + LIST_CMD + "]");
            out.println("scr " + LIST_CMD + " [ <bundleId> ]");
            out.println("scr " + INFO_CMD + " <componentId>");
            out.println("scr " + ENABLE_CMD + " <componentId>");
            out.println("scr " + DISABLE_CMD + " <componentId>");
            out.println("scr " + CONFIG_CMD);
        }
    }
}
