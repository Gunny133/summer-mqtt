package com.github.christophersmith.summer.mqtt.paho.service;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.netcrusher.core.reactor.NioReactor;
import org.netcrusher.tcp.TcpCrusher;
import org.netcrusher.tcp.TcpCrusherBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import com.github.christophersmith.summer.mqtt.core.MqttClientConnectionType;
import com.github.christophersmith.summer.mqtt.core.MqttQualityOfService;
import com.github.christophersmith.summer.mqtt.core.event.MqttClientConnectedEvent;
import com.github.christophersmith.summer.mqtt.core.event.MqttClientConnectionFailureEvent;
import com.github.christophersmith.summer.mqtt.core.event.MqttClientConnectionLostEvent;
import com.github.christophersmith.summer.mqtt.core.event.MqttClientDisconnectedEvent;
import com.github.christophersmith.summer.mqtt.core.event.MqttConnectionStatusEvent;

public class AutomaticReconnectTest implements ApplicationListener<MqttConnectionStatusEvent>
{
    private static final String BROKER_URI_FORMAT           = "tcp://%s:%s";
    private static final String LOCAL_HOST_NAME             = "localhost";
    private static final int    LOCAL_HOST_PORT             = 10080;
    private static final String BROKER_HOST_NAME            = "localhost";
    // private static final String BROKER_HOST_NAME = "iot.eclipse.org";
    private static final int    BROKER_PORT                 = 1883;
    private static final String CLIENT_ID                   = MqttAsyncClient.generateClientId();
    private static NioReactor   REACTOR;
    private static TcpCrusher   CRUSHER_PROXY;
    private AtomicInteger       clientConnectedCount        = new AtomicInteger(0);
    private AtomicInteger       clientDisconnectedCount     = new AtomicInteger(0);
    private AtomicInteger       clientLostConnectionCount   = new AtomicInteger(0);
    private AtomicInteger       clientFailedConnectionCount = new AtomicInteger(0);

    @BeforeClass
    public static void initialize() throws IOException
    {
        REACTOR = new NioReactor();
        CRUSHER_PROXY = TcpCrusherBuilder.builder().withReactor(REACTOR)
            .withBindAddress(LOCAL_HOST_NAME, LOCAL_HOST_PORT)
            .withConnectAddress(BROKER_HOST_NAME, BROKER_PORT).buildAndOpen();
    }

    @AfterClass
    public static void shutdown()
    {
        CRUSHER_PROXY.close();
        REACTOR.close();
    }

    private StaticApplicationContext getStaticApplicationContext()
    {
        clientConnectedCount.set(0);
        clientDisconnectedCount.set(0);
        clientLostConnectionCount.set(0);
        clientFailedConnectionCount.set(0);
        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.addApplicationListener(this);
        applicationContext.refresh();
        applicationContext.start();
        return applicationContext;
    }


    @Test
    public void testGoodConnection() throws MqttException
    {
        StaticApplicationContext applicationContext = getStaticApplicationContext();
        MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
        PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
            String.format(BROKER_URI_FORMAT, LOCAL_HOST_NAME, String.valueOf(LOCAL_HOST_PORT)),
            CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
        service.setApplicationEventPublisher(applicationContext);
        service.setInboundMessageChannel(inboundMessageChannel);
        service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
        service.getMqttConnectOptions().setCleanSession(true);
        Assert.assertTrue(service.start());
        Assert.assertTrue(service.isConnected());
        Assert.assertTrue(service.isStarted());
        Assert.assertEquals(1, clientConnectedCount.get());
        Assert.assertEquals(0, clientDisconnectedCount.get());
        Assert.assertEquals(0, clientLostConnectionCount.get());
        Assert.assertEquals(0, clientFailedConnectionCount.get());
        service.stop();
        service.close();
        applicationContext.close();
    }

    @Test
    public void testMqttConnectOptionsAutomaticReconnectDefaultServerAvailableAtStartup()
        throws MqttException, InterruptedException
    {
        StaticApplicationContext applicationContext = getStaticApplicationContext();
        MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
        PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
            String.format(BROKER_URI_FORMAT, LOCAL_HOST_NAME, String.valueOf(LOCAL_HOST_PORT)),
            CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
        service.setApplicationEventPublisher(applicationContext);
        service.setInboundMessageChannel(inboundMessageChannel);
        service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
        service.getMqttConnectOptions().setCleanSession(true);
        Assert.assertTrue(service.start());
        Assert.assertTrue(service.isConnected());
        Assert.assertTrue(service.isStarted());
        // simulate a lost connection
        CRUSHER_PROXY.reopen();
        Assert.assertFalse(service.isStarted());
        Assert.assertFalse(service.isConnected());
        Thread.sleep(1000);
        Assert.assertFalse(service.isStarted());
        Assert.assertFalse(service.isConnected());
        Assert.assertEquals(1, clientConnectedCount.get());
        Assert.assertEquals(0, clientDisconnectedCount.get());
        Assert.assertEquals(1, clientLostConnectionCount.get());
        Assert.assertEquals(0, clientFailedConnectionCount.get());
        service.stop();
        service.close();
        applicationContext.close();
    }

    @Test
    public void testMqttConnectOptionsAutomaticReconnectFalseServerAvailableAtStartup()
        throws MqttException, InterruptedException
    {
        StaticApplicationContext applicationContext = getStaticApplicationContext();
        MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
        PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
            String.format(BROKER_URI_FORMAT, LOCAL_HOST_NAME, String.valueOf(LOCAL_HOST_PORT)),
            CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
        service.setApplicationEventPublisher(applicationContext);
        service.setInboundMessageChannel(inboundMessageChannel);
        service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
        service.getMqttConnectOptions().setCleanSession(true);
        service.getMqttConnectOptions().setAutomaticReconnect(false);
        Assert.assertTrue(service.start());
        Assert.assertTrue(service.isConnected());
        Assert.assertTrue(service.isStarted());
        // simulate a lost connection
        CRUSHER_PROXY.reopen();
        Assert.assertFalse(service.isStarted());
        Assert.assertFalse(service.isConnected());
        Thread.sleep(1000);
        Assert.assertFalse(service.isStarted());
        Assert.assertFalse(service.isConnected());
        Assert.assertEquals(1, clientConnectedCount.get());
        Assert.assertEquals(0, clientDisconnectedCount.get());
        Assert.assertEquals(1, clientLostConnectionCount.get());
        Assert.assertEquals(0, clientFailedConnectionCount.get());
        service.stop();
        service.close();
        applicationContext.close();
    }

    @Test
    public void testMqttConnectOptionsAutomaticReconnectTrueServerAvailableAtStartup()
        throws MqttException, InterruptedException
    {
        StaticApplicationContext applicationContext = getStaticApplicationContext();
        MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
        PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
            String.format(BROKER_URI_FORMAT, LOCAL_HOST_NAME, String.valueOf(LOCAL_HOST_PORT)),
            CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
        service.setApplicationEventPublisher(applicationContext);
        service.setInboundMessageChannel(inboundMessageChannel);
        service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
        service.getMqttConnectOptions().setCleanSession(true);
        service.getMqttConnectOptions().setAutomaticReconnect(true);
        Assert.assertTrue(service.start());
        Assert.assertTrue(service.isConnected());
        Assert.assertTrue(service.isStarted());
        // simulate a lost connection
        CRUSHER_PROXY.reopen();
        Assert.assertFalse(service.isStarted());
        Assert.assertFalse(service.isConnected());
        Thread.sleep(1100);
        Assert.assertTrue(service.isConnected());
        Assert.assertTrue(service.isStarted());
        Assert.assertEquals(2, clientConnectedCount.get());
        Assert.assertEquals(0, clientDisconnectedCount.get());
        Assert.assertEquals(1, clientLostConnectionCount.get());
        Assert.assertEquals(0, clientFailedConnectionCount.get());
        service.stop();
        service.close();
        applicationContext.close();
    }

    @Test
    public void testMqttConnectOptionsAutomaticReconnectDefaultServerUnavailableAtStartup()
        throws MqttException, InterruptedException
    {
        StaticApplicationContext applicationContext = getStaticApplicationContext();
        CRUSHER_PROXY.close();
        MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
        PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
            String.format(BROKER_URI_FORMAT, LOCAL_HOST_NAME, String.valueOf(LOCAL_HOST_PORT)),
            CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
        service.setApplicationEventPublisher(applicationContext);
        service.setInboundMessageChannel(inboundMessageChannel);
        service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
        service.getMqttConnectOptions().setCleanSession(true);
        Assert.assertFalse(service.start());
        Assert.assertFalse(service.isConnected());
        Assert.assertFalse(service.isStarted());
        Thread.sleep(1000);
        CRUSHER_PROXY.open();
        Assert.assertFalse(service.isConnected());
        Assert.assertFalse(service.isStarted());
        Thread.sleep(1100);
        Assert.assertFalse(service.isConnected());
        Assert.assertFalse(service.isStarted());
        Assert.assertEquals(0, clientConnectedCount.get());
        Assert.assertEquals(0, clientDisconnectedCount.get());
        Assert.assertEquals(0, clientLostConnectionCount.get());
        Assert.assertEquals(1, clientFailedConnectionCount.get());
        service.stop();
        service.close();
        applicationContext.close();
    }

    @Test
    public void testMqttConnectOptionsAutomaticReconnectFalseServerUnavailableAtStartup()
        throws MqttException, InterruptedException
    {
        StaticApplicationContext applicationContext = getStaticApplicationContext();
        CRUSHER_PROXY.close();
        MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
        PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
            String.format(BROKER_URI_FORMAT, LOCAL_HOST_NAME, String.valueOf(LOCAL_HOST_PORT)),
            CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
        service.setApplicationEventPublisher(applicationContext);
        service.setInboundMessageChannel(inboundMessageChannel);
        service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
        service.getMqttConnectOptions().setCleanSession(true);
        service.getMqttConnectOptions().setAutomaticReconnect(false);
        Assert.assertFalse(service.start());
        Assert.assertFalse(service.isConnected());
        Assert.assertFalse(service.isStarted());
        Thread.sleep(1000);
        CRUSHER_PROXY.open();
        Assert.assertFalse(service.isConnected());
        Assert.assertFalse(service.isStarted());
        Thread.sleep(1100);
        Assert.assertFalse(service.isConnected());
        Assert.assertFalse(service.isStarted());
        Assert.assertEquals(0, clientConnectedCount.get());
        Assert.assertEquals(0, clientDisconnectedCount.get());
        Assert.assertEquals(0, clientLostConnectionCount.get());
        Assert.assertEquals(1, clientFailedConnectionCount.get());
        service.stop();
        service.close();
        applicationContext.close();
    }

    @Override
    public void onApplicationEvent(MqttConnectionStatusEvent event)
    {
        if (event instanceof MqttClientConnectedEvent)
        {
            clientConnectedCount.incrementAndGet();
        }
        if (event instanceof MqttClientConnectionLostEvent)
        {
            clientLostConnectionCount.incrementAndGet();
        }
        if (event instanceof MqttClientDisconnectedEvent)
        {
            clientDisconnectedCount.incrementAndGet();
        }
        if (event instanceof MqttClientConnectionFailureEvent)
        {
            clientFailedConnectionCount.incrementAndGet();
        }
    }

    /*
     * @Test public void testMqttConnectOptionsAutomaticReconnectTrueServerUnavailableAtStartup()
     * throws MqttException, InterruptedException { MessageChannel inboundMessageChannel = new
     * ExecutorSubscribableChannel(); PahoAsyncMqttClientService service = new
     * PahoAsyncMqttClientService( String.format(SERVER_URI_FORMAT,
     * String.valueOf(proxy.getLocalPort())), CLIENT_ID, MqttClientConnectionType.PUBSUB, null);
     * service.setInboundMessageChannel(inboundMessageChannel);
     * service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
     * service.getMqttConnectOptions().setCleanSession(true);
     * service.getMqttConnectOptions().setAutomaticReconnect(true); proxy.disableProxy();
     * Assert.assertFalse(service.start()); Assert.assertFalse(service.isConnected());
     * Assert.assertFalse(service.isStarted()); proxy.enableProxy(); Thread.sleep(1100);
     * Assert.assertFalse(service.isConnected()); Assert.assertFalse(service.isStarted());
     * proxy.disableProxy(); Assert.assertFalse(service.isStarted());
     * Assert.assertFalse(service.isConnected()); proxy.enableProxy(); Thread.sleep(1100);
     * Assert.assertFalse(service.isConnected()); Assert.assertFalse(service.isStarted());
     * service.stop(); service.close(); }
     */

    /*
     * @Test public void testReconnectDetailsSetServerAvailableAtStartup() throws MqttException,
     * InterruptedException { TaskScheduler taskScheduler = new ConcurrentTaskScheduler();
     * MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
     * PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
     * String.format(SERVER_URI_FORMAT, String.valueOf(proxy.getLocalPort())), CLIENT_ID,
     * MqttClientConnectionType.PUBSUB, null);
     * service.setInboundMessageChannel(inboundMessageChannel);
     * service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
     * service.getMqttConnectOptions().setCleanSession(true); service.setReconnectDetails(new
     * DefaultReconnectService(), taskScheduler); proxy.enableProxy();
     * Assert.assertTrue(service.start()); Assert.assertTrue(service.isConnected());
     * Assert.assertTrue(service.isStarted()); proxy.disableProxy();
     * Assert.assertFalse(service.isStarted()); Assert.assertFalse(service.isConnected());
     * proxy.enableProxy(); Thread.sleep(1100); Assert.assertFalse(service.isStarted());
     * Assert.assertFalse(service.isConnected()); Thread.sleep(2000);
     * Assert.assertTrue(service.isStarted()); Assert.assertTrue(service.isConnected());
     * proxy.disableProxy(); Thread.sleep(1100); service.stop(); service.close(); }
     */

    /*
     * @Test public void testReconnectDetailsSetServerUnavailableAtStartup() throws MqttException,
     * InterruptedException { TaskScheduler taskScheduler = new ConcurrentTaskScheduler();
     * MessageChannel inboundMessageChannel = new ExecutorSubscribableChannel();
     * PahoAsyncMqttClientService service = new PahoAsyncMqttClientService(
     * String.format(SERVER_URI_FORMAT, String.valueOf(proxy.getLocalPort())), CLIENT_ID,
     * MqttClientConnectionType.PUBSUB, null);
     * service.setInboundMessageChannel(inboundMessageChannel);
     * service.subscribe(String.format("client/%s", CLIENT_ID), MqttQualityOfService.QOS_0);
     * service.getMqttConnectOptions().setCleanSession(true); service.setReconnectDetails(new
     * DefaultReconnectService(), taskScheduler); proxy.disableProxy();
     * Assert.assertFalse(service.start()); Assert.assertFalse(service.isConnected());
     * Assert.assertFalse(service.isStarted()); proxy.enableProxy(); Thread.sleep(3100);
     * Assert.assertTrue(service.isConnected()); Assert.assertTrue(service.isStarted());
     * proxy.disableProxy(); Assert.assertFalse(service.isStarted());
     * Assert.assertFalse(service.isConnected()); proxy.enableProxy(); Thread.sleep(3100);
     * Assert.assertTrue(service.isConnected()); Assert.assertTrue(service.isStarted());
     * service.stop(); service.close(); }
     */
}