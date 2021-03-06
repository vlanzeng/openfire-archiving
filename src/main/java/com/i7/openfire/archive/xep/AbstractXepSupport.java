package com.i7.openfire.archive.xep;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.handler.IQHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import com.i7.openfire.archive.plugin.ArchivingPlugin;

public abstract class AbstractXepSupport {
	private static final Logger log = LoggerFactory.getLogger(AbstractXepSupport.class);

	protected final String namespace;
	protected final XMPPServer server;
	protected final IQHandler iqDispatcher;
	protected final Map<String, IQHandler> element2Handlers;

	protected Collection<IQHandler> iqHandlers;

	public AbstractXepSupport(XMPPServer server, String namespace, String iqDispatcherNamespace,
			String iqDispatcherName) {

		this.server = server;
		this.element2Handlers = Collections.synchronizedMap(new HashMap<String, IQHandler>());
		this.iqDispatcher = new AbstractIQHandler(iqDispatcherName, null, iqDispatcherNamespace) {
			@Override
			public IQ handleIQ(IQ packet) throws UnauthorizedException {
				if (!ArchivingPlugin.getInstance().isEnabled()) {
					return error(packet, PacketError.Condition.feature_not_implemented);
				}

				final IQHandler iqHandler = element2Handlers.get(packet.getChildElement().getName());
				if (iqHandler != null) {
					return iqHandler.handleIQ(packet);
				} else {
					return error(packet, PacketError.Condition.feature_not_implemented);
				}
			}
		};
		this.namespace = namespace;
		this.iqHandlers = Collections.emptyList();

	}

	public void start() {
		for (IQHandler iqHandler : iqHandlers) {
			try {
				iqHandler.initialize(server);
				iqHandler.start();
			} catch (Exception e) {
				log.error("Unable to initialize and start " + iqHandler.getClass());
				continue;
			}

			element2Handlers.put(iqHandler.getInfo().getName(), iqHandler);
			if (iqHandler instanceof ServerFeaturesProvider) {
				for (Iterator<String> i = ((ServerFeaturesProvider) iqHandler).getFeatures(); i.hasNext();) {
					server.getIQDiscoInfoHandler().addServerFeature(i.next());
				}
			}
		}
		server.getIQDiscoInfoHandler().addServerFeature(namespace);
		server.getIQRouter().addHandler(iqDispatcher);
	}

	public void stop() {
		IQRouter iqRouter = server.getIQRouter();
		IQDiscoInfoHandler iqDiscoInfoHandler = server.getIQDiscoInfoHandler();

		for (IQHandler iqHandler : iqHandlers) {
			element2Handlers.remove(iqHandler.getInfo().getName());
			try {
				iqHandler.stop();
				iqHandler.destroy();
			} catch (Exception e) {
				log.warn("Unable to stop and destroy " + iqHandler.getClass());
			}

			if (iqHandler instanceof ServerFeaturesProvider) {
				for (Iterator<String> i = ((ServerFeaturesProvider) iqHandler).getFeatures(); i.hasNext();) {
					if (iqDiscoInfoHandler != null) {
						iqDiscoInfoHandler.removeServerFeature(i.next());
					}
				}
			}
		}
		if (iqRouter != null) {
			iqRouter.removeHandler(iqDispatcher);
		}
	}

}
