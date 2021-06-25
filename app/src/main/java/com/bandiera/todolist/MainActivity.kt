package com.bandiera.todolist

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bandiera.todolist.databinding.ActivityMainBinding
import com.bandiera.todolist.databinding.ItemTodoBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private val RC_SIGN_IN = 1000

    private lateinit var binding: ActivityMainBinding

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 로그인 안 됨
        if (FirebaseAuth.getInstance().currentUser == null) {
            // Choose authentication providers
            login()

        }


        binding.todoRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = TodoAdapter(
                emptyList(),
                onClickDeleteImageView = {
                    viewModel.removeTodo(it)
//                        binding.todoRecyclerView.adapter?.notifyDataSetChanged()
                },
                onClickToggleTextView = {
                    viewModel.toggleTodo(it)
//                        binding.todoRecyclerView.adapter?.notifyDataSetChanged()
                }
            )
        }

        binding.addButton.setOnClickListener {
            val todo = Todo(binding.todoEditText.text.toString(), false)
            viewModel.addTodo(todo)
//            binding.todoRecyclerView.adapter?.notifyDataSetChanged()
            binding.todoEditText.text = null
        }

        // 관찰 UI 업데이트
        viewModel.todoLiveData.observe(this, Observer {
            (binding.todoRecyclerView.adapter as TodoAdapter).setData(it)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                // Successfully signed in
//                val user = FirebaseAuth.getInstance().currentUser
                viewModel.fetchData()
            } else {
                // Failed sign in
                finish()
            }
        }
    }

    fun login() {
        val providers = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build())

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN
        )
    }

    fun logout() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                login()
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_log_out -> {
                logout()
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }
}

data class Todo(val title: String, var completed: Boolean)


class TodoAdapter(
    private var dataSet: List<DocumentSnapshot>,
    val onClickDeleteImageView: (todo: DocumentSnapshot) -> Unit,
    val onClickToggleTextView: (todo: DocumentSnapshot) -> Unit
) :
    RecyclerView.Adapter<TodoAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTodoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_todo, viewGroup, false)

        return ViewHolder(ItemTodoBinding.bind(view))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val todo = dataSet[position]

        viewHolder.binding.apply {
            titleTextView.text = todo.getString("title") ?: ""
            val isComplete : Boolean = todo.getBoolean("completed") ?: false

            if (isComplete) {
                titleTextView.paintFlags = titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                titleTextView.setTypeface(null, Typeface.ITALIC)
            } else {
                titleTextView.paintFlags = 0
                titleTextView.setTypeface(null, Typeface.NORMAL)
            }

            deleteImageView.setOnClickListener {
                onClickDeleteImageView(todo)
            }

            titleTextView.setOnClickListener {
                onClickToggleTextView(todo)
            }
        }

    }

    override fun getItemCount() = dataSet.size

    fun setData(newData: List<DocumentSnapshot>) {
        dataSet = newData
        notifyDataSetChanged()
    }

}

class MainViewModel : ViewModel() {
    val db = Firebase.firestore

    var todoLiveData = MutableLiveData<List<DocumentSnapshot>>()

    init {
        fetchData()
    }

    fun fetchData() {
        val user = FirebaseAuth.getInstance().currentUser

        if (user != null) {
            db.collection(user.uid)
                .addSnapshotListener { value, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    if (value != null) {
                        todoLiveData.value = value.documents
                    }

                }
        }

    }

    fun addTodo(todo: Todo) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            db.collection(user.uid)
                .add(todo)
        }
    }

    fun removeTodo(todo: DocumentSnapshot) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            db.collection(user.uid).document(todo.id)
                .delete()
        }
    }

    fun toggleTodo(todo: DocumentSnapshot) {
        FirebaseAuth.getInstance().currentUser?.let { user ->
            db.collection(user.uid).document(todo.id)
                .update("completed", !(todo.getBoolean("completed") ?: false))
        }
    }

}
