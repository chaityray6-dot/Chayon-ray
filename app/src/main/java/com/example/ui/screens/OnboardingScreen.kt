package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.PrimaryBrandGradient
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val imageRes: Int
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val strings = LocalAppStrings.current
    val slides = listOf(
        OnboardingSlide(
            title = "Read & Reflow Effortlessly",
            subtitle = "Single page, continuous scroll, two-page book view, text reflow, Sepia comfort, and Android Text-to-Speech.",
            icon = Icons.Filled.AutoAwesome,
            imageRes = R.drawable.img_onboarding_art
        ),
        OnboardingSlide(
            title = "Scan, Convert & Annotate",
            subtitle = "Perspective smart scanner, Image & Office (DOCX/XLSX/PPTX) conversion, freehand ink, forms & digital signatures.",
            icon = Icons.Filled.DocumentScanner,
            imageRes = R.drawable.img_hero_banner
        ),
        OnboardingSlide(
            title = "Offline-First Privacy Vault",
            subtitle = "AES-256 Keystore vault encryption, PIN & biometric lock, PDF passwords, watermarks, and OCR text extraction.",
            icon = Icons.Filled.Lock,
            imageRes = R.drawable.img_onboarding_art
        )
    )
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_art),
                        contentDescription = "DocuVerse",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(strings.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onFinish, modifier = Modifier.testTag("onboarding_skip_btn")) {
                    Text(strings.skip)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val slide = slides[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = slide.imageRes),
                        contentDescription = slide.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(28.dp))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryBrandGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(slide.icon, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(slides.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(if (selected) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (pagerState.currentPage < slides.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("onboarding_next_btn"),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (pagerState.currentPage == slides.lastIndex) strings.getStarted else strings.next)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}
