package com.lightbend.java.standard.modelserver.queriablestate;

import com.lightbend.java.configuration.kafka.ApplicationKafkaParameters;
import com.lightbend.java.model.ModelServingInfo;
import com.lightbend.java.standard.modelserver.store.StoreState;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 *  A simple REST proxy that runs embedded in the Model server. This is used to
 *  demonstrate how a developer can use the Interactive Queries APIs exposed by Kafka Streams to
 *  locate and query the State Stores within a Kafka Streams Application.
 *  https://github.com/confluentinc/examples/blob/3.2.x/kafka-streams/src/main/java/io/confluent/examples/streams/interactivequeries/WordCountInteractiveQueriesRestService.java
 */
@Path("state")
public class QueriesRestService {

    private final KafkaStreams streams;
    private final MetadataService metadataService;
    private Server jettyServer;
    private int port;

    public QueriesRestService(final KafkaStreams streams) {
        this.streams = streams;
        this.metadataService = new MetadataService(streams);
    }

    /**
     * Get the metadata for all of the instances of this Kafka Streams application
     * @return List of {@link HostStoreInfo}
     */
    @GET()
    @Path("/instances")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HostStoreInfo> streamsMetadata() {
        return metadataService.streamsMetadataForStore(ApplicationKafkaParameters.STORE_NAME, port);
    }

    /**
     * Get current value of the of state
     * @return {@link ModelServingInfo} representing the key-value pair
     */
    @GET
    @Path("/value")
    @Produces(MediaType.APPLICATION_JSON)
    public ModelServingInfo servingInfo() {
        // Get the  Store
        final ReadOnlyKeyValueStore<Integer, StoreState> store =
                streams.store(ApplicationKafkaParameters.STORE_NAME, QueryableStoreTypes.<Integer, StoreState>keyValueStore());
        if (store == null) {
            throw new NotFoundException();
        }
        ModelServingInfo info = store.get(ApplicationKafkaParameters.STORE_ID).getCurrentServingInfo();
        return info == null ? ModelServingInfo.empty : info;
    }

    /**
     * Start an embedded Jetty Server on the given port
     * @param port    port to run the Server on
     * @throws Exception
     */
    public void start(final int port) throws Exception {

        this.port = port;
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        jettyServer = new Server(port);
        jettyServer.setHandler(context);

        ResourceConfig rc = new ResourceConfig();
        rc.register(this);
        rc.register(JacksonFeature.class);

        ServletContainer sc = new ServletContainer(rc);
        ServletHolder holder = new ServletHolder(sc);
        context.addServlet(holder, "/*");

        jettyServer.start();
        System.out.println("Starting models observer at " + jettyServer.getURI());
    }

    /**
     * Stop the Jetty Server
     * @throws Exception
     */
    public void stop() throws Exception {
        if (jettyServer != null) {
            jettyServer.stop();
        }
    }

}
