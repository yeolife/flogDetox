package com.ssafy.frogdetox.data.remote

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.ssafy.frogdetox.data.local.SharedPreferencesManager.getUId
import com.ssafy.frogdetox.data.model.TodoDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch

private const val TAG = "TodoRepository_싸피"
class TodoRepository {
    private val myRef = Firebase.database.getReference("Todo").apply {
        keepSynced(true)
    }
    private val uidQuery = myRef.orderByChild("uid").equalTo(getUId())

    fun getData(selectDay : Long) : LiveData<MutableList<TodoDto>> {
        val mutableData = MutableLiveData<MutableList<TodoDto>>()

        uidQuery.addValueEventListener(object : ValueEventListener {
            val listData : MutableList<TodoDto> = mutableListOf()

            override fun onDataChange(snapshot: DataSnapshot) {
                listData.clear()
                if(snapshot.exists()) {
                    for(curSnapshot in snapshot.children) {
                        val getData = curSnapshot.getValue(TodoDto::class.java)
                        if (getData != null && selectDay == getData.regTime) {
                            listData.add(getData)
                        }
                    }
                }
                mutableData.value = listData
            }

            override fun onCancelled(error: DatabaseError) { }
        })

        return mutableData
    }
    suspend fun getTodo(): String {
        val deferred = CompletableDeferred<String>()

        var result = ""
        var count = 0
        val dtoList = arrayListOf<TodoDto>()
        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (curSnapshot in snapshot.children) {
                        val getData = curSnapshot.getValue(TodoDto::class.java)
                        if (getData != null && getData.uId== getUId()) {
                            dtoList.add(getData)
                        }
                    }
                    for(i in 0 ..< dtoList.size){
                        result += dtoList[dtoList.size-1-i].content + ", "
                        count++
                        if (count >= 10) break
                    }

                    deferred.complete(result)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                deferred.completeExceptionally(error.toException())
            }
        })

        return deferred.await()
    }
    suspend fun todoSelect(id: String): TodoDto {
        return withContext(Dispatchers.IO) {
            var todo = TodoDto()

            // count 만큼의 이벤트를 대기함
            val latch = CountDownLatch(1)

            myRef.child(id).get().addOnSuccessListener { it ->
                Log.i("firebase", "Got value ${it.value}")
                it.getValue(TodoDto::class.java)?.let { data ->
                    todo = data
                }
                latch.countDown()
            }.addOnFailureListener {
                Log.e("firebase", "Error getting data", it)
                latch.countDown()
            }

            // 모든 이벤트가 끝나면 await에서 대기하고 있던 스레드가 해제됨
            latch.await()

            return@withContext todo
        }
    }

    fun todoDeleteAll() {
        uidQuery.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (curSnapshot in snapshot.children) {
                        curSnapshot.ref.removeValue()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) { }
        })
    }

    fun todoInsert(todo: TodoDto) {
        val key = myRef.push().key.toString()

        todo.id = key
        Log.d(TAG, "todoInsert: $key")
        myRef.child(key).setValue(todo)
    }

    fun todoContentUpdate(todo: TodoDto) {
        val childUpdates: Map<String, Any> = mapOf("content" to todo.content, "alarm" to todo.isAlarm,"alarmCode" to todo.alarmCode,"alarmTime" to todo.alarmTime)
        myRef.child(todo.id).updateChildren(childUpdates)
    }

    fun todoCheckUpdate(id: String, complete: Boolean) {
        val childUpdates: Map<String, Any> = mapOf("complete" to complete)
        myRef.child(id).updateChildren(childUpdates)
    }

    fun todoDelete(id: String) {
        myRef.child(id).removeValue()
    }
}