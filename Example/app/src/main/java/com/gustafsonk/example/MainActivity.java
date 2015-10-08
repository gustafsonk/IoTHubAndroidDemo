package com.gustafsonk.example;

import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.microsoft.azure.iothub.DeviceClient;
import com.microsoft.azure.iothub.IotHubClientProtocol;
import com.microsoft.azure.iothub.IotHubEventCallback;
import com.microsoft.azure.iothub.IotHubMessageResult;
import com.microsoft.azure.iothub.IotHubStatusCode;
import com.microsoft.azure.iothub.Message;
import com.microsoft.azure.iothub.MessageCallback;

public class MainActivity extends AppCompatActivity {
    private final long CLIENT_TIMEOUT_MILLISECONDS = 30000L;

    // After a sent message is acknowledged
    private class MyEventCallback implements IotHubEventCallback {
        public void execute(IotHubStatusCode statusCode, Object context) {
            System.out.println("A sent message was acknowledged by IoT Hub.");
            MyCallbackContext callbackContext = (MyCallbackContext) context;
            String response = new StringBuilder()
                    .append("IoT Hub responded to message '")
                    .append(callbackContext.Message)
                    .append("' with status ")
                    .append(statusCode.name())
                    .toString();
            HandleSendMessageResponse(response);
        }
    }

    // After a received message
    private class MyMessageCallback implements MessageCallback {
        public IotHubMessageResult execute(Message message, Object context) {
            System.out.println("A message was received from IoT Hub.");
            String response = new String(message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET);
            HandleReceiveMessageResponse(response);

            // Success (something expected happened on the device leading to a positive result)
            // IotHubMessageResult.COMPLETE

            // Failure (something expected happened on the device leading to a negative result)
            // IotHubMessageResult.REJECT

            // Error (something unexpected happened on the device leading to an unknown result)
            // IotHubMessageResult.ABANDON

            return IotHubMessageResult.COMPLETE;
        }
    }

    // Give context after a triggered callback
    private class MyCallbackContext {
        public String Message;

        public MyCallbackContext(String message) {
            Message = message;
        }
    }

    // Utility class for UI components
    private class Fields {
        public String HubId;
        public String DeviceId;
        public String DeviceKey;
        public String MessageData;
        public IotHubClientProtocol Protocol;

        public Fields(String hubId, String deviceId, String deviceKey, String messageData, IotHubClientProtocol protocol) {
            HubId = hubId;
            DeviceId = deviceId;
            DeviceKey = deviceKey;
            MessageData = messageData;
            Protocol = protocol;
        }

        public String GetConnectionString() {
            return new StringBuilder()
                    .append("HostName=")
                    .append(HubId)
                    .append(";DeviceId=")
                    .append(DeviceId)
                    .append(";SharedAccessKey=")
                    .append(DeviceKey)
                    .toString();
        }
    }

    // Utility method for UI components
    private Fields GetFields() {
        String hubId = ParseEditText(R.id.hubIdField);
        String deviceId = ParseEditText(R.id.deviceIdField);
        String deviceKey = ParseEditText(R.id.deviceKeyField);
        String messageData = ParseEditText(R.id.messageDataField);

        IotHubClientProtocol protocol = GetSelectedProtocol();
        return new Fields(hubId, deviceId, deviceKey, messageData, protocol);
    }

    // Utility method for UI components
    private IotHubClientProtocol GetSelectedProtocol() {
        IotHubClientProtocol protocol;
        int protocolId = GetRadioGroupSelectedId(R.id.protocolGroup);
        if (protocolId == R.id.httpsRadio) {
            protocol = IotHubClientProtocol.HTTPS;
        }
        else if (protocolId == R.id.amqpsRadio) {
            protocol = IotHubClientProtocol.AMQPS;
        }
        else {
            System.out.println("Protocol unknown/not set, defaulting to HTTPS.");
            protocol = IotHubClientProtocol.HTTPS;
        }

        return protocol;
    }

    // Utility method for UI components
    private String ParseEditText(int id) {
        EditText field = (EditText) findViewById(id);
        return field.getText().toString().trim();
    }

    // Utility method for UI components
    private int GetRadioGroupSelectedId(int id) {
        RadioGroup group = (RadioGroup) findViewById(id);
        return group.getCheckedRadioButtonId();
    }

    // Utility method for UI components
    private void SetTextView(int id, final String text) {
        final TextView textView = (TextView) findViewById(id);
        runOnUiThread(new Runnable() {
            public void run() {
                textView.setText(text);
            }
        });
    }

    // Activity initialization logic
    private void Init() {
        // Send Message button press
        Button sendMessage = (Button) findViewById(R.id.sendMessageButton);
        sendMessage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new SendOneMessageTask().execute();
            }
        });

        // Receive Message button press
        Button receiveMessage = (Button) findViewById(R.id.receiveMessageButton);
        receiveMessage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                new ReceiveOneMessageTask().execute();
            }
        });
    }

    // Make send message networking run on a separate thread from the UI
    private class SendOneMessageTask extends AsyncTask<Void, Void, Void> {
        private DeviceClient client;

        protected void onPreExecute() {
            StartTimerForCloseClient(client, CLIENT_TIMEOUT_MILLISECONDS);
        }

        protected Void doInBackground(Void... params) {
            Fields fields = GetFields();
            client = SendOneMessage(fields.GetConnectionString(), fields.Protocol, fields.MessageData);
            return null;
        }
    }

    // Make receive message networking run on a separate thread from the UI
    private class ReceiveOneMessageTask extends AsyncTask<Void, Void, Void> {
        private DeviceClient client;

        protected void onPreExecute() {
            StartTimerForCloseClient(client, CLIENT_TIMEOUT_MILLISECONDS);
        }

        protected Void doInBackground(Void... params) {
            Fields fields = GetFields();
            client = ReceiveOneMessage(fields.GetConnectionString(), fields.Protocol);
            return null;
        }
    }

    // Attempt to send a message to IoT Hub
    private DeviceClient SendOneMessage(String connectionString, IotHubClientProtocol protocol, String messageData) {
        System.out.println("Attempting to send a message.");
        DeviceClient client = null;
        try {
            client = new DeviceClient(connectionString, protocol);

            client.open();

            Message msg = new Message(messageData);
            MyEventCallback callback = new MyEventCallback();
            MyCallbackContext callbackContext = new MyCallbackContext(messageData);
            client.sendEventAsync(msg, callback, callbackContext);
        }
        catch (Exception ex) {
            HandleSendMessageResponse(ex.getMessage());
        }
        return client;
    }

    // Attempt to receive a message from IoT Hub
    private DeviceClient ReceiveOneMessage(String connectionString, IotHubClientProtocol protocol) {
        System.out.println("Attempting to receive a message.");
        DeviceClient client = null;
        try {
            client = new DeviceClient(connectionString, protocol);

            client.open();

            MyMessageCallback callback = new MyMessageCallback();
            MyCallbackContext callbackContext = new MyCallbackContext(null);
            client.setMessageCallback(callback, callbackContext);
        }
        catch (Exception ex) {
            HandleReceiveMessageResponse(ex.getMessage());
        }
        return client;
    }

    // Display a sent message response
    private void HandleSendMessageResponse(String message) {
        System.out.println(message);
        SetTextView(R.id.sendMessageLabel, message);
    }

    // Display a received message response
    private void HandleReceiveMessageResponse(String message) {
        System.out.println(message);
        SetTextView(R.id.receiveMessageLabel, message);
    }

    // Forcibly close the client after a period of time if it's not already closed
    private void StartTimerForCloseClient(final DeviceClient client, final long milliseconds) {
        runOnUiThread(new Runnable() {
            public void run() {
                new CountDownTimer(milliseconds, milliseconds) {
                    public void onTick(long millisUntilFinished) {
                    }

                    public void onFinish() {
                        CloseClient(client);
                    }
                }.start();
            }
        });
    }

    // Safely close the client
    private void CloseClient(DeviceClient client) {
        System.out.println("Attempting to close a client.");
        try {
            if (client != null) {
                client.close();
            }
        }
        catch (Exception ex) {
            System.out.println("Failed to close a client: " + ex.getMessage());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Init();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
