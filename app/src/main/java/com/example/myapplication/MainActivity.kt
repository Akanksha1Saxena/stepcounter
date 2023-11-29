package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.material3.Card
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Button
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import android.content.Context
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable



class MainActivity : ComponentActivity(), SensorEventListener, LocationListener {

    private lateinit var sensor_managing: SensorManager
    private var sensor_step: Sensor? = null
    private val _steps = mutableStateOf(0)

    private lateinit var loc_manager: LocationManager
    private var lastseen_location: Location? = null

    private var tot_dist_meters: Float = 0f
    private var calories_burned: Double = 0.0
    private var current_speed: Float = 0f
    private var sleeping_status = false
    private var sleep_status_msg = "Asleep"
    private val total_steps_count = mutableStateOf(10000)



    companion object {
        private const val REQUEST_CODE_ACTIVITY_RECOGNITION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("StepCounter", "onCreate: Activity created.")

        // Check for Activity Recognition permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_CODE_ACTIVITY_RECOGNITION)
        } else {
            Step_Sensor()
            Location_Manager()
        }

        setContent {
            val navigation = rememberNavController()
            MyApplicationTheme {
                NavHost(navController  = navigation, startDestination = "welcome") {
                    composable("welcome") {
                        WelcomeScreen(onArrowClick = { navigation.navigate("details") })
                    }
                    composable("details") {
                        StepCounterApp(
                            steps = _steps.value,
                            totalDistance = tot_dist_meters,
                            calories_burned = calories_burned,
                            speed = current_speed,
                            sleepStatus = sleep_status_msg,
                            totalSteps = total_steps_count.value,
                            onFoodSuggestionClick = {navigation.navigate("encouragementAndFood")  }
                        )
                    }
                    composable("encouragementAndFood") {
                        EncouragementAndFoodScreen(
                            steps = _steps.value,
                            calories_burned = calories_burned,
                            onBackClick = {
                                navigation.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
    private fun Step_Sensor() {
        sensor_managing = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor_step = sensor_managing.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (sensor_step == null) {

            Log.d("StepCounter", "No Step Sensor found!")
            return
        }

        sensor_managing.registerListener(this, sensor_step, SensorManager.SENSOR_DELAY_UI)
        Log.d("StepCounter", "Sensor listener registered.")
    }
    private fun Location_Manager() {
        loc_manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            loc_manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000,  // 1-second interval for updates
                0.1f,  // 0.1 meters minimum distance between updates
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        // Calculate speed, distance, and calories burnt here
        val last_Location = lastseen_location

        if (last_Location != null) {
            current_speed = location.speed  // speed in m/s
            val distance = location.distanceTo(last_Location)
            tot_dist_meters += distance
            val weightKg = 70.0  // Replace with the user's weight in kilograms
            val met = 8.0  // MET value for the activity (adjust as needed)
            val timeInSeconds = 1.0  // Time in seconds
            calories_burned += (met * weightKg * timeInSeconds) / 3600

        }
        lastseen_location = location
    }


    override fun onResume() {
        super.onResume()
        sensor_step?.also { sensor ->
            sensor_managing.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            Log.d("StepCounter", "onResume: Sensor listener registered.")
        }
        val accelerometer_Sensor = sensor_managing.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer_Sensor != null) {
            sensor_managing.registerListener(this, accelerometer_Sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("StepCounter", "onResume: Accelerometer sensor listener registered.")
        } else {

            Log.d("StepCounter", "No Accelerometer Sensor found!")
        }
    }

    override fun onPause() {
        super.onPause()
        sensor_managing.unregisterListener(this)
        Log.d("StepCounter", "onPause: Sensor listener unregistered.")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val current_Steps = event.values[0].toInt()
            _steps.value = current_Steps
            Log.d("StepCounter", "onSensorChanged: Steps detected - $current_Steps")
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            Log.d("StepCounter", "raw data: $x,$y,$z")
            // Calculate the magnitude of acceleration
            val cal_acceleration = Math.sqrt((x * x + y * y + z * z).toDouble())
            Log.d("StepCounter", "acceleration: $cal_acceleration")
            Log.d("StepCounter", "sleeping_status: $sleeping_status")

            // Set an activity threshold to detect inactivity
            val cal_inactivity_threshold = 10.2

            if (cal_acceleration < cal_inactivity_threshold && !sleeping_status) {
                // User sleeping
                sleeping_status = true
                sleep_status_msg = "Asleep"
                Log.d("StepCounter", "Sleep detected.")
            } else if (cal_acceleration >= cal_inactivity_threshold && sleeping_status) {
                // User woke up
                sleeping_status = false
                sleep_status_msg = "Hey! You are Awake"
                Log.d("StepCounter", "Woke up.")
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Can be implemented if needed
    }
    @Deprecated("Method deprecated")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_ACTIVITY_RECOGNITION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Step_Sensor()
                } else {

                    Log.d("StepCounter", "Activity Recognition permission denied.")
                }
            }

        }
    }
}
@Composable
fun EncouragementAndFoodScreen(steps: Int, calories_burned: Double, onBackClick: () -> Unit) {
    // Calculate the encouragement message based on the number of steps
    val Message = if (steps < 5000) {
        val remain_steps = 5000-steps
        "Great progress with $steps steps! Only $remain_steps more to healthier lifestyle. Keep going, you're doing amazing!"
    } else {
        "Great job! Keep it up!"
    }

    // Calculate food suggestion based on calories burned
    val food_suggestion = when {
        calories_burned <= 500 -> "Consider a light meal."
        calories_burned <= 1000 -> "Opt for a healthy sandwich."
        else -> "You've burned a lot of calories! Treat yourself with a healthy meal."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = Message, style = MaterialTheme.typography.titleLarge, color=Color.hsl(hue = 200f, saturation = 1f, lightness = 0.25f))

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),

                ) {
                Image(
                    painter = painterResource(id = R.drawable.food_icon),
                    contentDescription = "Food Icon",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Food Suggestion",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = food_suggestion,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onBackClick() },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Back")
        }
    }
}

@Composable
fun StepCounterApp(
    steps: Int,
    totalSteps: Int,
    totalDistance: Float,
    calories_burned: Double,
    speed: Float,
    sleepStatus: String,
    onFoodSuggestionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hello, Fitness Enthusiast", style = MaterialTheme.typography.titleLarge, color = Color.hsl(hue = 200f, saturation = 1f, lightness = 0.25f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .padding(16.dp)
                .background(
                    color = Color.Transparent,
                    shape = CircleShape
                )
                .border(
                    border = BorderStroke(
                        5.dp,
                        Color.hsl(hue = 200f, saturation = 1f, lightness = 0.25f)
                    ),
                    shape = CircleShape
                )
        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.footprint),
                    contentDescription = "footprint",
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "$steps/$totalSteps",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }


        }


        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.speed),
                    contentDescription = "Speed Icon",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Speed: ${"%.2f".format(speed)} m/s",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.distance),
                    contentDescription = "Speed Icon",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Total Distance: ${"%.2f".format(totalDistance / 1000)} km",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.calories),
                    contentDescription = "Speed Icon",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Calories Burned: ${"%.2f".format(calories_burned)} kcal",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sleep),
                    contentDescription = "Speed Icon",
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Sleep Status: $sleepStatus",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = { onFoodSuggestionClick() },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = "Food Suggestion")
        }
    }
}



@Composable
fun WelcomeScreen(onArrowClick: () -> Unit) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF2196F3), Color(0xFF0D47A1)),
        startY = 0f,
        endY = 500f
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBrush)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.run_circle_white_36dp),
            contentDescription = null,
            modifier = Modifier
                .size(200.dp)


        )

        Text(
            text = "Welcome to FitnessTracker",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Image(
            painter = painterResource(id = R.drawable.arrow),
            contentDescription = "Go to details",
            modifier = Modifier.clickable(onClick = onArrowClick)
                .size(100.dp)


        )
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        StepCounterApp(
            steps = 123,
            totalDistance = 0f,
            calories_burned = 0.0,
            speed = 0f,
            sleepStatus = "Asleep",
            totalSteps = 10000,
            onFoodSuggestionClick = {}
        )
    }
}


