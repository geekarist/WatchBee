package me.cpele.fleabrainer.ui

import android.animation.Animator
import android.annotation.SuppressLint
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.cpele.fleabrainer.R
import me.cpele.fleabrainer.domain.GoalTiming
import me.cpele.fleabrainer.domain.Status
import me.cpele.fleabrainer.repository.BeeRepository

private const val ARG_ACCESS_TOKEN = "ACCESS_TOKEN"

class MainActivity : AppCompatActivity(), GoalGeneralViewHolder.Listener {

    private lateinit var mAdapter: GoalAdapter
    private var mMenu: Menu? = null

    private lateinit var repository: BeeRepository

    private val extraAuthToken
        get() = intent.getStringExtra(ARG_ACCESS_TOKEN)

    companion object {
        fun start(context: Context, token: String) {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(ARG_ACCESS_TOKEN, token)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }
    }

    private val viewModel: MainViewModel
        get() = ViewModelProviders
                .of(this, MainViewModel.Factory(repository, extraAuthToken))
                .get(MainViewModel::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(localClassName, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = CustomApp.instance.beeRepository

        supportActionBar?.title = getString(R.string.app_name)

        if (savedInstanceState == null) {
            viewModel.refresh()
            sendBroadcast(BeeJobReceiver.CustomIntent(extraAuthToken))
        }

        mAdapter = GoalAdapter(this)
    }

    override fun onResume() {
        super.onResume()

        main_rv.adapter = mAdapter

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            main_rv.layoutManager = LinearLayoutManager(this)
        } else {
            main_rv.layoutManager = GridLayoutManager(this, 2)
            main_rv.addItemDecoration(CenterMarginFix())
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPersist(goalTiming: GoalTiming) {
        viewModel.persist(goalTiming)
    }

    override fun onSubmit(goalTiming: GoalTiming) {
        viewModel.submit(this, goalTiming)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        Log.d(localClassName, "onCreateOptionsMenu")
        val displayMenu = super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_options_menu, menu)
        mMenu = menu

        viewModel.status.observe(this, Observer {
            Log.d(localClassName, "Activity received status: $it")
            triggerSyncStatus(it?.status ?: Status.LOADING)
            if (it?.status == Status.AUTH_ERROR) {
                SignInActivity.start(context = this@MainActivity, clearToken = true)
            }
            it?.message?.apply {
                Toast.makeText(this@MainActivity, this, Toast.LENGTH_LONG).show()
            }
        })

        viewModel.goalTimings.observe(this, Observer {
            Log.d(localClassName, "Activity received goals: $it")
            supportActionBar?.subtitle = it?.firstOrNull()?.user
            mAdapter.refresh(it ?: emptyList())
        })

        return displayMenu
    }

    private fun triggerSyncStatus(status: Status) = when (status) {
        Status.SUCCESS -> succeedSyncAnim()
        Status.LOADING -> startSyncAnim()
        Status.FAILURE, Status.AUTH_ERROR -> failSyncAnim()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        Log.d(localClassName, "onOptionsItemSelected")
        return when (item?.itemId) {
            R.id.main_menu_sync -> {
                viewModel.refresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("InflateParams")
    private fun startSyncAnim() {

        Log.d(localClassName, "startSyncAnim")

        val syncAnimation = AnimationUtils.loadAnimation(this, R.anim.anim_sync)
        val syncActionView = LayoutInflater.from(this).inflate(R.layout.view_action_sync, null)
        syncActionView.startAnimation(syncAnimation)

        val item = mMenu?.findItem(R.id.main_menu_sync)
        item?.actionView?.clearAnimation()
        item?.actionView = syncActionView
        item?.setIcon(R.drawable.ic_sync_white_24dp)
    }

    private fun succeedSyncAnim() {
        Log.d(localClassName, "succeedSyncAnim")
        val item = mMenu?.findItem(R.id.main_menu_sync)
        item?.actionView?.clearAnimation()
        item?.actionView
                ?.animate()
                ?.alpha(0f)
                ?.setDuration(500)
                ?.setListener(object: Animator.AnimatorListener {
                    override fun onAnimationRepeat(animation: Animator?) {
                    }

                    override fun onAnimationCancel(animation: Animator?) {
                    }

                    override fun onAnimationStart(animation: Animator?) {
                        val itemImageView: ImageView? = item.actionView as ImageView?
                        itemImageView?.setImageResource(R.drawable.ic_check_circle_white_24dp)
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        item.setIcon(R.drawable.ic_sync_white_24dp)
                        item.actionView = null
                    }
                })
                ?.start()
    }

    private fun failSyncAnim() {
        Log.d(localClassName, "failSyncAnim")
        val item = mMenu?.findItem(R.id.main_menu_sync)
        item?.actionView?.clearAnimation()
        item?.actionView = null
        item?.setIcon(R.drawable.ic_sync_problem_white_24dp)
    }

    override fun onOpen(goalTiming: GoalTiming) {
        DetailActivity.start(this, goalTiming.user, goalTiming.goal.slug)
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        main_rv.adapter = null
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
