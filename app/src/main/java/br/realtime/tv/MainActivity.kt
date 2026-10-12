package br.realtime.tv

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout

abstract class Screen(val act: MainActivity) {
    abstract val view: View
    open fun onShow() {}
    open fun onHide() {}
    /** devolve true se tratou o "voltar" */
    open fun back(): Boolean = false
}

object PlayerHolder {
    var list: List<Entry> = emptyList()
    var index: Int = 0
}

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private val stack = ArrayList<Screen>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(C.BG))
        root = FrameLayout(this)
        root.setBackgroundColor(C.BG)
        setContentView(root)
        val logged = Store.user.isNotEmpty() && Store.pass.isNotEmpty()
        reset(IntroScreen(this, logged))
    }

    private fun attach(s: Screen) {
        root.addView(s.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        stack.add(s)
        s.onShow()
    }

    fun push(s: Screen) {
        val top = stack.lastOrNull()
        if (top != null) {
            top.onHide()
            top.view.visibility = View.GONE
        }
        attach(s)
    }

    fun reset(s: Screen) {
        for (x in stack) {
            x.onHide()
            root.removeView(x.view)
        }
        stack.clear()
        attach(s)
    }

    fun pop() {
        if (stack.size <= 1) return
        val top = stack.removeAt(stack.size - 1)
        top.onHide()
        root.removeView(top.view)
        val prev = stack.last()
        prev.view.visibility = View.VISIBLE
        prev.onShow()
    }

    fun openEntry(e: Entry, list: List<Entry>? = null, index: Int = 0) {
        when (e.t) {
            "live" -> if (list != null) play(list, index) else play(listOf(e), 0)
            "movie", "series" -> push(DetailScreen(this, e))
            else -> play(listOf(e), 0)
        }
    }

    fun play(list: List<Entry>, index: Int) {
        PlayerHolder.list = list
        PlayerHolder.index = index
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val top = stack.lastOrNull()
        if (top != null && top.back()) return
        if (stack.size > 1) pop() else finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val top = stack.lastOrNull()
        if (top is IntroScreen && event.action == KeyEvent.ACTION_DOWN) {
            top.skip()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        stack.lastOrNull()?.onShow()
    }

    override fun onPause() {
        stack.lastOrNull()?.onHide()
        super.onPause()
    }
}
