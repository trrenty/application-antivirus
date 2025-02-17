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
package com.xwiki.antivirus.internal;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.quartz.SchedulerException;
import org.quartz.Trigger.TriggerState;
import org.xwiki.classloader.ClassLoaderManager;
import org.xwiki.classloader.xwiki.internal.ContextNamespaceURLClassLoader;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.extension.event.ExtensionInstalledEvent;
import org.xwiki.extension.repository.internal.installed.DefaultInstalledExtension;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.plugin.scheduler.JobState;
import com.xpn.xwiki.plugin.scheduler.SchedulerPlugin;

/**
 * Ensure that AntivirusJob is scheduled after antivirus install. Reschedule AntivirusJob to work around XWIKI-14494:
 * 'Java scheduler job coming from an extension is not rescheduled when the extension is upgraded'. The unschedule /
 * schedule process should be removed once the issue is fixed and licensing depends on a version of XWiki >= the version
 * where is fixed.
 *
 * @version $Id$
 * @since 1.4.3
 */
@Component
@Named(AntivirusJobSchedulerListener.ROLE_HINT)
@Singleton
public class AntivirusJobSchedulerListener extends AbstractEventListener implements Initializable
{
    /**
     * The role hint of this component.
     */
    public static final String ROLE_HINT = "AntivirusJobSchedulerListener";

    /**
     * The id of application-antivirus-api module.
     */
    protected static final String ANTIVIRUS_API_ID = "com.xwiki.antivirus:application-antivirus-api";

    protected static final LocalDocumentReference JOB_DOC =
        new LocalDocumentReference(Collections.singletonList("Antivirus"), "AntivirusJob");

    private static final List<Event> EVENTS = Collections.singletonList(new ExtensionInstalledEvent());

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private ClassLoaderManager classLoaderManager;

    public AntivirusJobSchedulerListener()
    {
        super(ROLE_HINT, EVENTS);
    }

    /**
     * The unschedule / schedule process should be done at ExtensionUpgradedEvent, but for avoiding XCOMMONS-751:
     * 'Getting wrong component instance during JAR extension upgrade' it is done at initialization step, since when the
     * extension is initialized after an upgrade, all the extension's listeners are initialized. After the issue is
     * fixed and licensing starts depending on a version of XWiki >= the version where is fixed, then this code should
     * be moved inside a ExtensionUpgradedEvent listener.
     *
     * @see org.xwiki.component.phase.Initializable#initialize()
     */
    @Override
    public void initialize() throws InitializationException
    {
        // Overwrite the Thread Context ClassLoader to work around the https://jira.xwiki.org/browse/XCOMMONS-2064 bug.
        // Remove this hack once it's fixed and licensing starts depending on XWiki >= the version where it's fixed.
        Thread.currentThread().setContextClassLoader(
            new ContextNamespaceURLClassLoader(this.wikiDescriptorManager, this.classLoaderManager));

        try {
            // Don't trigger the rescheduling process at xwiki startup time.
            if (this.contextProvider.get() != null) {
                scheduleAutomaticUpgradesJob(true);
            }
        } catch (XWikiException | SchedulerException e) {
            throw new InitializationException("Error while rescheduling AntivirusJob", e);
        }
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        String extensionId = ((DefaultInstalledExtension) source).getId().getId();

        if (event instanceof ExtensionInstalledEvent && extensionId.equals(ANTIVIRUS_API_ID)) {
            try {
                scheduleAutomaticUpgradesJob(false);
            } catch (XWikiException | SchedulerException e) {
                throw new RuntimeException("Error while scheduling AntivirusJob after antivirus install",
                    e);
            }
        }
    }

    protected void scheduleAutomaticUpgradesJob(boolean doReschedule) throws XWikiException, SchedulerException
    {
        XWikiContext xcontext = contextProvider.get();

        SchedulerPlugin scheduler = (SchedulerPlugin) xcontext.getWiki().getPluginManager().getPlugin("scheduler");
        XWikiDocument jobDoc = xcontext.getWiki().getDocument(JOB_DOC, xcontext);
        BaseObject job = jobDoc.getXObject(SchedulerPlugin.XWIKI_JOB_CLASSREFERENCE);
        JobState jobState = scheduler.getJobStatus(job, xcontext);

        if (doReschedule && jobState.getQuartzState().equals(TriggerState.NORMAL)) {
            scheduler.unscheduleJob(job, xcontext);
            scheduler.scheduleJob(job, xcontext);
        } else if (!doReschedule && jobState.getQuartzState().equals(TriggerState.NONE)) {
            scheduler.scheduleJob(job, xcontext);
        }
    }
}
