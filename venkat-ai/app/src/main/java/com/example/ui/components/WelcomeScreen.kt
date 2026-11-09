package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.GeminiBlue
import com.example.ui.theme.GeminiCyan
import com.example.ui.theme.GeminiPurple

data class SuggestionPrompt(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val icon: ImageVector,
    val isTamil: Boolean = false
)

val SampleSuggestions = listOf(
    SuggestionPrompt(
        title = "வணக்கம்! (Hello!)",
        subtitle = "நீங்கள் யார்? உங்களுக்கு என்ன தெரியும்?",
        prompt = "வணக்கம்! நீங்கள் யார்? உங்களுக்கு என்னென்ன திறமைகள் உள்ளன?",
        icon = Icons.Default.Translate,
        isTamil = true
    ),
    SuggestionPrompt(
        title = "திருக்குறள் விளக்கம்",
        subtitle = "அகர முதல எழுத்தெல்லாம் குறளின் பொருள்",
        prompt = "'அகர முதல எழுத்தெல்லாம் ஆதி பகவன் முதற்றே உலகு' - இந்த திருக்குறளின் எளிய விளக்கம் மற்றும் கருத்து என்ன?",
        icon = Icons.Default.Lightbulb,
        isTamil = true
    ),
    SuggestionPrompt(
        title = "Kotlin Compose",
        subtitle = "Write a glowing animated card button",
        prompt = "Write a production-ready Jetpack Compose component that has a glowing neon gradient border with smooth breathing pulse animation in Kotlin.",
        icon = Icons.Default.Code
    ),
    SuggestionPrompt(
        title = "Image Analysis",
        subtitle = "Attach any photo or screenshot to analyze",
        prompt = "Can you analyze images and extract key details, summaries or text from them?",
        icon = Icons.Default.Image
    ),
    SuggestionPrompt(
        title = "Draft Professional Email",
        subtitle = "Polite leave request or proposal",
        prompt = "Draft a polite, professional leave request email to a manager for 2 days due to a personal emergency.",
        icon = Icons.Default.Mail
    ),
    SuggestionPrompt(
        title = "Simple Explanation",
        subtitle = "Explain how LLMs and Gemini work",
        prompt = "Explain how large language models and Gemini AI work in simple everyday terms with an analogy.",
        icon = Icons.Default.AutoAwesome
    )
)

@Composable
fun WelcomeScreen(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_aura")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("welcome_screen")
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(28.dp))

            // Glowing Star Emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                // Background radial glow
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GeminiCyan.copy(alpha = 0.45f),
                                    GeminiPurple.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // App Icon Drawable
                Image(
                    painter = painterResource(id = R.drawable.venkat_ai_star_icon),
                    contentDescription = "VENKAT AI Star",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(GeminiCyan, GeminiPurple)
                            ),
                            shape = CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Welcome Title with Gradient Text Styling
            Text(
                text = "Hello, I'm VENKAT AI",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your Intelligent AI Companion",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = GeminiCyan,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dual Language Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = GeminiBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Supports English & தமிழ் (Tamil)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GeminiCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Device Assistant Duties (Voice / Tap)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Scrollable Duties Row
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(
                    listOf(
                        "🐱 Hi Kitty" to "Hi kitty",
                        "📞 Call Arun" to "Call Arun",
                        "📞 அம்மாவுக்கு கால்" to "அம்மாவுக்கு கால் செய்",
                        "📞 माँ को कॉल" to "माँ को कॉल करो",
                        "⚡ Start AI" to "Hey AI, Start On-Device AI",
                        "🔦 Torch On" to "Turn on torch",
                        "🔦 Torch Off" to "Turn off torch",
                        "📷 Camera" to "Open Camera",
                        "🖼️ Gallery" to "Open Gallery",
                        "🧮 Calculator" to "Open Calculator",
                        "🌐 Browser" to "Open Browser",
                        "⚙️ Settings" to "Open Settings",
                        "📶 Wi-Fi" to "Turn on Wi-Fi",
                        "🔵 Bluetooth" to "Turn on Bluetooth",
                        "🔊 Volume +" to "Increase Volume",
                        "🔉 Volume -" to "Decrease Volume",
                        "🔋 Battery" to "Battery Status",
                        "📱 Device Info" to "Device Information",
                        "📸 Instagram" to "Open Instagram",
                        "▶️ YouTube" to "Open YouTube",
                        "💡 Explain This" to "Explain this quantum computing",
                        "🌐 Translate" to "Translate this hello friend",
                        "💾 Remember" to "Remember this: my meeting is at 4 PM",
                        "❓ Help" to "Help",
                        "💤 Shutdown" to "Hey AI, Shutdown"
                    )
                ) { (label, prompt) ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GeminiCyan.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .clickable { onSelectPrompt(prompt) }
                            .testTag("duty_chip_$label")
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GeminiPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Suggestions to get started",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Suggestions List
        items(SampleSuggestions) { suggestion ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .testTag("suggestion_${suggestion.title}")
                    .clickable { onSelectPrompt(suggestion.prompt) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = if (suggestion.isTamil) {
                                        listOf(GeminiPurple.copy(alpha = 0.2f), GeminiCyan.copy(alpha = 0.2f))
                                    } else {
                                        listOf(GeminiBlue.copy(alpha = 0.2f), GeminiPurple.copy(alpha = 0.2f))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = suggestion.icon,
                            contentDescription = null,
                            tint = if (suggestion.isTamil) GeminiCyan else GeminiBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = suggestion.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
