package com.neaera.autonotifier

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.neaera.autonotifier.ui.theme.AutoNotifierTheme
import java.util.Calendar
import kotlin.random.Random

private const val ACTION_REPLY = "com.example.REPLY"
private const val ACTION_MARK_AS_READ = "com.example.MARK_AS_READ"

private const val EXTRA_CONVERSATION_ID_KEY = "conversation_id"
private const val REMOTE_INPUT_RESULT_KEY = "reply_input"

private val channel_id = "jacob_testing";
private val channel_name = "jacob_testing_name";
private val channel_description = "jacob_testing_description";

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        enableEdgeToEdge()
        setContent {
            AutoNotifierTheme {
                    MainPage(
                        onClick = {notify(this, Random.nextInt())}
                    )
            }
        }
    }


    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channel_id, channel_name, importance).apply {
            description = channel_description
        }

        // Register the channel with the system.
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates a [RemoteInput] that lets remote apps provide a response string
     * to the underlying [Intent] within a [PendingIntent].
     */
    private fun createReplyRemoteInput(context: Context): RemoteInput {
        // RemoteInput.Builder accepts a single parameter: the key to use to store
        // the response in.
        return RemoteInput.Builder(REMOTE_INPUT_RESULT_KEY).build()
        // Note that the RemoteInput has no knowledge of the conversation. This is
        // because the data for the RemoteInput is bound to the reply Intent using
        // static methods in the RemoteInput class.
    }

    /** Creates an [Intent] that handles replying to the given conversation. */
    private fun createReplyIntent(
        context: Context, notificationId: Int): Intent {
        // Creates the intent backed by the MessagingService.
        val intent = Intent(context, MainActivity::class.java)

        // Lets the MessagingService know this is a reply request.
        intent.action = ACTION_REPLY

        // Provides the ID of the conversation that the reply applies to.
        intent.putExtra(EXTRA_CONVERSATION_ID_KEY, notificationId)

        return intent
    }

    /** Creates an [Intent] that handles marking the conversation as read. */
    private fun createMarkAsReadIntent(
        context: Context, notificationId: Int): Intent {
        val intent = Intent(context, MainActivity::class.java)
        intent.action = ACTION_MARK_AS_READ
        intent.putExtra(EXTRA_CONVERSATION_ID_KEY, notificationId)
        return intent
    }

    private fun createMarkAsReadAction(
        context: Context, notificationId: Int): NotificationCompat.Action {
        val markAsReadIntent = createMarkAsReadIntent(context, notificationId)
        val markAsReadPendingIntent = PendingIntent.getService(
            context,
            12345, // Method explained below.
            markAsReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT  or PendingIntent.FLAG_IMMUTABLE)
        val markAsReadAction = NotificationCompat.Action.Builder(
            R.drawable.baseline_close_24, "Mark as Read", markAsReadPendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        return markAsReadAction
    }

    private fun createReplyAction(
        context: Context, notificationId: Int): NotificationCompat.Action {
        val replyIntent: Intent = createReplyIntent(context, notificationId)

        val replyPendingIntent = PendingIntent.getService(
            context,
            12345, // Method explained later.
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val replyAction = NotificationCompat.Action.Builder(R.drawable.baseline_add_24, "Reply", replyPendingIntent)
            // Provides context to what firing the Action does.
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)

            // The action doesn't show any UI, as required by Android Auto.
            .setShowsUserInterface(false)

            // Don't forget the reply RemoteInput. Android Auto will use this to
            // make a system call that will add the response string into
            // the reply intent so it can be extracted by the messaging app.
            .addRemoteInput(createReplyRemoteInput(context))
            .build()

        return replyAction
    }

    private fun createMessagingStyle(
        context: Context, notificationId: Int): NotificationCompat.MessagingStyle {
        // Method defined by the messaging app.
        val appDeviceUser = "Notif. Muncher"

        val devicePerson = Person.Builder()
            // The display name (also the name that's read aloud in Android auto).
            .setName(appDeviceUser)

            // The icon to show in the notification shade in the system UI (outside
            // of Android Auto).
            .setIcon(IconCompat.createWithResource(context, R.drawable.baseline_boy_24))

            // A unique key in case there are multiple people in this conversation with
            // the same name.
            .setKey(appDeviceUser)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(devicePerson)

        // Sets the conversation title. If the app's target version is lower
        // than P, this will automatically mark the conversation as a group (to
        // maintain backward compatibility). Use `setGroupConversation` after
        // setting the conversation title to explicitly override this behavior. See
        // the documentation for more information.
        messagingStyle.setConversationTitle("")

        // Group conversation means there is more than 1 recipient, so set it as such.
        messagingStyle.setGroupConversation(false)

        val senderPerson = Person.Builder()
            .setName(appDeviceUser.toString())
            .setIcon(IconCompat.createWithResource(context, R.drawable.baseline_boy_24))
            .setKey(appDeviceUser.toString())
            .build()

        // Adds the message. More complex messages, like images,
        // can be created and added by instantiating the MessagingStyle.Message
        // class directly. See documentation for details.
        messagingStyle.addMessage(
            "Nom Nom...", Calendar.getInstance().time.toInstant().toEpochMilli(), senderPerson)

        return messagingStyle
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun notify(context: Context, notificationId: Int) {
        // Creates the actions and MessagingStyle.
        val replyAction = createReplyAction(context, notificationId)
        val markAsReadAction = createMarkAsReadAction(context, notificationId)
        val messagingStyle = createMessagingStyle(context, notificationId)

        // Creates the notification.
        val notification = NotificationCompat.Builder(context, channel_id)
            // A required field for the Android UI.
            .setSmallIcon(R.drawable.cookie_monster__1_)
            .setCategory(Notification.CATEGORY_MESSAGE)

            // Shows in Android Auto as the conversation image.
            .setLargeIcon(drawableToBitmap(ContextCompat.getDrawable(context, R.drawable.cookie_monster__1_)!!))

            // Adds MessagingStyle.
            .setStyle(messagingStyle)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)

            // Adds reply action.
            .addAction(replyAction)

            // Makes the mark-as-read action invisible, so it doesn't appear
            // in the Android UI but the app satisfies Android Auto's
            // mark-as-read Action requirement. Both required actions can be made
            // visible or invisible; it is a stylistic choice.
            .addInvisibleAction(markAsReadAction)

            .build()

        // Posts the notification for the user to see.
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
            return
        }
        notificationManagerCompat.notify(notificationId, notification)
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainPage(onClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text("Notification Muncher")
                }
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment=Alignment.CenterHorizontally,
        ) {
            Button(onClick=onClick, modifier=Modifier.padding(10.dp)) {
                Icon(
                    modifier = Modifier.height(30.dp),
                    imageVector= ImageVector.vectorResource(R.drawable.cookie),
                    contentDescription = "Cookie"
                )
                Text(
                    fontSize= 20.sp,
                    text = "  Feed Me Cookies"
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AutoNotifierTheme {
        MainPage(onClick= { })
    }
}