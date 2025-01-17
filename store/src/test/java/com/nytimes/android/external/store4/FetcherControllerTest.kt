package com.nytimes.android.external.store4

import com.nytimes.android.external.store4.ResponseOrigin.Fetcher
import com.nytimes.android.external.store4.StoreResponse.Data
import com.nytimes.android.external.store4.impl.FetcherController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@ExperimentalCoroutinesApi
@FlowPreview
@RunWith(JUnit4::class)
class FetcherControllerTest {
    private val testScope = TestCoroutineScope()
    @Test
    fun simple() = testScope.runBlockingTest {
        val fetcherController = FetcherController<Int, Int, Int>(
                scope = testScope,
                realFetcher = { key: Int ->
                    flow {
                        emit(key * key)
                    }
                },
                sourceOfTruth = null
        )
        val fetcher = fetcherController.getFetcher(3)
        assertThat(fetcherController.fetcherSize()).isEqualTo(0)
        val received = fetcher.onEach {
            assertThat(fetcherController.fetcherSize()).isEqualTo(1)
        }.first()
        assertThat(received).isEqualTo(
                Data(
                        value = 9,
                        origin = Fetcher
                )
        )
        assertThat(fetcherController.fetcherSize()).isEqualTo(0)
    }

    @Test
    fun concurrent() = testScope.runBlockingTest {
        var createdCnt = 0
        val fetcherController = FetcherController<Int, Int, Int>(
                scope = testScope,
                realFetcher = { key: Int ->
                    createdCnt++
                    flow {
                        // make sure it takes time, otherwise, we may not share
                        delay(1)
                        emit(key * key)
                    }
                },
                sourceOfTruth = null
        )
        val fetcherCount = 20
        fun createFetcher() = async {
            fetcherController.getFetcher(3)
                    .onEach {
                        assertThat(fetcherController.fetcherSize()).isEqualTo(1)
                    }.first()
        }

        val fetchers = (0 until fetcherCount).map {
            createFetcher()
        }
        fetchers.forEach {
            assertThat(it.await()).isEqualTo(
                    Data(
                            value = 9,
                            origin = Fetcher
                    )
            )
        }
        assertThat(fetcherController.fetcherSize()).isEqualTo(0)
        assertThat(createdCnt).isEqualTo(1)
    }
}
