package com.nytimes.android.external.store3

import com.nhaarman.mockitokotlin2.*
import com.nytimes.android.external.cache3.CacheBuilder
import com.nytimes.android.external.store4.Fetcher
import com.nytimes.android.external.store4.Persister
import com.nytimes.android.external.store4.fresh
import com.nytimes.android.external.store4.get
import com.nytimes.android.external.store4.legacy.BarCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ExperimentalCoroutinesApi
@RunWith(Parameterized::class)
class StoreTest(
    private val storeType: TestStoreType
) {
    private val testScope = TestCoroutineScope()
    private val counter = AtomicInteger(0)
    private val fetcher: Fetcher<String, BarCode> = mock()
    private var persister: Persister<String, BarCode> = mock()
    private val barCode = BarCode("key", "value")

    @Test
    fun testSimple() = testScope.runBlockingTest {
        val simpleStore = TestStoreBuilder.from(
            scope = testScope,
            fetcher = fetcher,
            persister = persister
        ).build(storeType)

        whenever(fetcher.invoke(barCode))
            .thenReturn(NETWORK)

        whenever(persister.read(barCode))
            .thenReturn(null)
            .thenReturn(DISK)

        whenever(persister.write(barCode, NETWORK))
            .thenReturn(true)

        var value = simpleStore.get(barCode)

        assertThat(value).isEqualTo(DISK)
        value = simpleStore.get(barCode)
        assertThat(value).isEqualTo(DISK)
        verify(fetcher, times(1)).invoke(barCode)
    }

    @Test
    fun testDoubleTap() = testScope.runBlockingTest {
        val simpleStore = TestStoreBuilder.from(
            scope = testScope,
            fetcher = fetcher,
            persister = persister
        ).build(storeType)
        whenever(fetcher.invoke(barCode))
            .thenAnswer {
                if (counter.incrementAndGet() == 1) {
                    NETWORK
                } else {
                    throw RuntimeException("Yo Dawg your inflight is broken")
                }
            }

        whenever(persister.read(barCode))
            .thenReturn(null)
            .thenReturn(DISK)

        whenever(persister.write(barCode, NETWORK))
            .thenReturn(true)


        val deferred = async { simpleStore.get(barCode) }
        simpleStore.get(barCode)
        deferred.await()

        verify(fetcher, times(1)).invoke(barCode)
    }

    @Test
    fun testSubclass() = testScope.runBlockingTest {

        val simpleStore = TestStoreBuilder.from(
            scope = testScope,
            fetcher = fetcher,
            persister = persister
        ).build(storeType)

        simpleStore.clear(barCode)

        whenever(fetcher.invoke(barCode))
            .thenReturn(NETWORK)

        whenever(persister.read(barCode))
            .thenReturn(null)
            .thenReturn(DISK)
        whenever(persister.write(barCode, NETWORK)).thenReturn(true)

        var value = simpleStore.get(barCode)
        assertThat(value).isEqualTo(DISK)
        value = simpleStore.get(barCode)
        assertThat(value).isEqualTo(DISK)
        verify(fetcher, times(1)).invoke(barCode)
    }


    @Test
    fun testEquivalence() = testScope.runBlockingTest {
        val cache = CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterAccess(java.lang.Long.MAX_VALUE, TimeUnit.SECONDS)
            .build<BarCode, String>()

        cache.put(barCode, MEMORY)
        var value = cache.getIfPresent(barCode)
        assertThat(value).isEqualTo(MEMORY)

        value = cache.getIfPresent(BarCode(barCode.type, barCode.key))
        assertThat(value).isEqualTo(MEMORY)
    }

    @Test
    fun testFreshUsesOnlyNetwork() = testScope.runBlockingTest {
        val simpleStore = TestStoreBuilder.from(
            scope = testScope,
            fetcher = fetcher,
            persister = persister
        ).build(storeType)

        whenever(fetcher.invoke(barCode)) doThrow RuntimeException(ERROR)

        whenever(persister.read(barCode)) doReturn DISK

        try {
            simpleStore.fresh(barCode)
            fail("Exception not thrown!")
        } catch (e: Exception) {
            assertThat(e.message).isEqualTo(ERROR)
        }

        verify(fetcher, times(1)).invoke(barCode)
        verify(persister, never()).read(any())
    }

    companion object {
        private const val DISK = "disk"
        private const val NETWORK = "fresh"
        private const val MEMORY = "memory"
        private const val ERROR = "error!!"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() = TestStoreType.values()
    }
}
