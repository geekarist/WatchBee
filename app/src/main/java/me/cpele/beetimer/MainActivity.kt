package me.cpele.beetimer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AnimationUtils
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private const val ARG_ACCESS_TOKEN = "ACCESS_TOKEN"
private const val CHILD_LOADING = 0
private const val CHILD_GOALS = 1
private const val CHILD_ERROR = 2

class MainActivity : AppCompatActivity() {

    private lateinit var mAdapter: GoalAdapter
    private var mMenu: Menu? = null

    companion object {
        fun start(context: Context, token: String) {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(ARG_ACCESS_TOKEN, token)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mAdapter = GoalAdapter()
        main_rv.adapter = mAdapter

        initiateFetch()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val displayMenu = super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_options_menu, menu)
        Handler().post { startSyncAnim() }
        mMenu = menu
        return displayMenu
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        return when (item?.itemId) {
            R.id.main_menu_sync -> {
                startSyncAnim()
                initiateFetch()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initiateFetch() {
        if (mAdapter.isEmpty()) main_vf.displayedChild = CHILD_LOADING
        fetchUser(intent.getStringExtra(ARG_ACCESS_TOKEN))
    }

    private fun fetchUser(accessToken: String) {

        CustomApp.instance.api.getUser(accessToken).enqueue(object : Callback<User> {
            override fun onFailure(call: Call<User>?, t: Throwable?) {
                Toast.makeText(this@MainActivity, "Error retrieving user: ${t.toString()}", Toast.LENGTH_LONG).show()
                failSyncAnim()
                if (mAdapter.isEmpty()) main_vf.displayedChild = CHILD_ERROR
            }

            override fun onResponse(call: Call<User>?, response: Response<User>?) {
                response?.body()?.username?.let {
                    fetchGoals(accessToken, it)
                }
            }
        })
    }

    private fun fetchGoals(accessToken: String, user: String) {

        CustomApp.instance.api.getGoals(user, accessToken).enqueue(object : Callback<List<Goal>> {
            override fun onFailure(call: Call<List<Goal>>?, t: Throwable?) {
                Toast.makeText(this@MainActivity, "Error retrieving goals: ${t.toString()}", Toast.LENGTH_LONG).show()
                failSyncAnim()
                if (mAdapter.isEmpty()) main_vf.displayedChild = CHILD_ERROR
            }

            override fun onResponse(call: Call<List<Goal>>?, response: Response<List<Goal>>?) {
                response?.body()?.let {
                    mAdapter.addAll(it)
                    succeedSyncAnim()
                    main_vf.displayedChild = CHILD_GOALS
                }
            }
        })
    }

    @SuppressLint("InflateParams")
    private fun startSyncAnim() {

        val syncAnimation = AnimationUtils.loadAnimation(this, R.anim.anim_sync)
        val syncActionView = LayoutInflater.from(this).inflate(R.layout.view_action_sync, null)
        syncActionView.startAnimation(syncAnimation)

        val item = mMenu?.findItem(R.id.main_menu_sync)
        item?.actionView = syncActionView
        item?.setIcon(R.drawable.ic_sync_problem_white_24dp)
    }

    private fun succeedSyncAnim() {
        val item = mMenu?.findItem(R.id.main_menu_sync)
        item?.actionView?.clearAnimation()
        item?.actionView = null
        item?.setIcon(R.drawable.ic_sync_white_24dp)
    }

    private fun failSyncAnim() {
        val item = mMenu?.findItem(R.id.main_menu_sync)
        item?.actionView?.clearAnimation()
        item?.actionView = null
        item?.setIcon(R.drawable.ic_sync_problem_white_24dp)
    }
}