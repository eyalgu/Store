package com.nytimes.android.external.fs3

import com.nytimes.android.external.store4.Persister
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

interface AllPersister<Raw, Key> : Persister<Raw, Key>, DiskAllRead<Raw>, DiskAllErase {
    /**
     * @param path to use to get data from persister
     * If data is not available implementer needs to
     * throw an exception
     */
    @Throws(FileNotFoundException::class)
    override suspend fun CoroutineScope.readAll(path: String): ReceiveChannel<Raw>

    /**
     * @param path to delete all the data in the the path.
     */
    override suspend fun deleteAll(path: String): Boolean

    /**
     * @param key to use to get data from persister
     * If data is not available implementer needs to
     * throw an exception
     */
    //    @Override
    override suspend fun read(key: Key): Raw?

    /**
     * @param key to use to store data to persister
     * @param raw raw string to be stored
     */
    override suspend fun write(key: Key, raw: Raw): Boolean
}
