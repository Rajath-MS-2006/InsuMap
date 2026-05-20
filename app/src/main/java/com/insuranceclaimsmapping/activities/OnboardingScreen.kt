package com.insuranceclaimsmapping.activities

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.insuranceclaimsmapping.R
import com.insuranceclaimsmapping.ui.components.AnimatedButton
import com.insuranceclaimsmapping.ui.theme.InsurerCobalt
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val iconRes: Int
)

val onboardingPages = listOf(
    OnboardingPage(
        "Welcome to InsuMap",
        "InsuMap connects Patients, Hospitals, and Insurers on one platform to make insurance claims fast, transparent, and smart.",
        R.drawable.logo
    ),
    OnboardingPage(
        "For Patients",
        "Submit claims, track your expenses, predict out-of-pocket costs, and view your digital insurance card — all in one place.",
        R.drawable.ic_medical_claim
    ),
    OnboardingPage(
        "For Hospitals",
        "Upload patient bills by scanning or PDF. Link bills to patient accounts using their Patient ID for seamless processing.",
        R.drawable.ic_add_claim
    ),
    OnboardingPage(
        "For Insurers",
        "Upload your policy PDF and let AI extract coverage rules. Adjudicate claims in bulk and monitor fraud alerts on your dashboard.",
        R.drawable.ic_profile
    ),
    OnboardingPage(
        "AI-Powered",
        "Powered by Google Gemini AI — bills are scanned, policies are parsed, and claims are adjudicated automatically with full reasoning.",
        R.drawable.ic_history
    )
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingPageContent(page = onboardingPages[page])
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .statusBarsPadding()
        ) {
            TextButton(onClick = onFinish) {
                Text("Skip", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PagerIndicator(
                pageCount = onboardingPages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            AnimatedButton(
                text = if (pagerState.currentPage == onboardingPages.size - 1) "Get Started" else "Next",
                onClick = {
                    if (pagerState.currentPage < onboardingPages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val isLogo = page.iconRes == R.drawable.logo
        Image(
            painter = painterResource(id = page.iconRes),
            contentDescription = page.title,
            modifier = Modifier
                .size(if (isLogo) 200.dp else 120.dp)
                .padding(bottom = 48.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (!isLogo) ColorFilter.tint(InsurerCobalt) else null
        )

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PagerIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until pageCount) {
            val isSelected = i == currentPage
            val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "indicator_width")
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) InsurerCobalt else Color.LightGray)
            )
        }
    }
}
