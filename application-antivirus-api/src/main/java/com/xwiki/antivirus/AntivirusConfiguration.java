/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.antivirus;

import org.xwiki.component.annotation.Role;

/**
 * Configuration of the Antivirus Application.
 *
 * @version $Id$
 */
@Role
public interface AntivirusConfiguration
{
    /**
     * @return {@code true} if scanning is enabled, for any of the attachment uploading, page creation or scheduled
     *         full-wiki scan operations
     */
    boolean isEnabled();

    /**
     * @return the name of the {@link AntivirusEngine} to use when scanning
     */
    String getDefaultEngineName();

    /**
     * @return {@code true} if the report at the end of a Scheduled Scan should always be sent to the admins, even when
     *         no infection has been detected
     */
    boolean shouldAlwaysSendReport();

    /**
     * @return the maximum file size in MB for which a scan should be performed at upload time
     */
    int getMaxFileSize();
}
