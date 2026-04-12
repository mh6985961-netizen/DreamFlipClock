package fr.gaetanlhf.dreamflipclock

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.card.MaterialCardView

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_back)
            .setOnClickListener { finish() }

        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        findViewById<TextView>(R.id.about_version_chip).text = version
        findViewById<TextView>(R.id.about_version_value).text = version

        findViewById<MaterialCardView>(R.id.card_github).setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://github.com/gaetanlhf/DreamFlipClock".toUri()
                )
            )
        }
    }
}
