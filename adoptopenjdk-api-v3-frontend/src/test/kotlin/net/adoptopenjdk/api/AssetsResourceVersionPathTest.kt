package net.adoptopenjdk.api

import io.quarkus.test.junit.QuarkusTest
import net.adoptopenjdk.api.v3.models.*
import org.junit.jupiter.api.DynamicTest
import java.util.stream.Stream


@QuarkusTest
class AssetsResourceVersionPathTest : AssetsPathTest() {


    fun getPath() = "/v3/assets/version"
    val JAVA8_212 = "8.0.212+4"
    val RANGE_11_12 = "[11.0.0,12.0.0]"
    val RANGE_8_METADATA = "[8.0.212+3,8.0.212+5]"
    val JAVA11 = "11.0.0+28"

    override fun <T> runFilterTest(filterParamName: String, values: Array<T>): Stream<DynamicTest> {

        return listOf(
                "[8.0.0,)",
                JAVA8_212,
                RANGE_11_12,
                RANGE_8_METADATA,
                JAVA11
        )
                .flatMap { versionRange ->
                    createTest(values, getPath() + "/" + versionRange, filterParamName, { element -> getExclusions(versionRange, element) })
                }
                .stream()
    }

    private fun <T> getExclusions(versionRange: String, element: T): Boolean {
        return versionRange.equals(JAVA8_212) && element == JvmImpl.openj9 ||
                versionRange.equals(JAVA8_212) && element == Architecture.aarch64 ||
                versionRange.equals(JAVA8_212) && element == Architecture.arm ||
                versionRange.equals(JAVA8_212) && element == HeapSize.large ||
                versionRange.equals(JAVA8_212) && element == ImageType.testimage ||

                versionRange.equals(RANGE_8_METADATA) && element == Architecture.aarch64 ||
                versionRange.equals(RANGE_8_METADATA) && element == Architecture.arm ||
                versionRange.equals(RANGE_8_METADATA) && element == ImageType.testimage ||

                versionRange.equals(RANGE_11_12) && element == OperatingSystem.solaris ||
                versionRange.equals(RANGE_11_12) && element == Architecture.sparcv9 ||

                versionRange.equals(JAVA11) && element == Architecture.x32 ||
                versionRange.equals(JAVA11) && element == OperatingSystem.solaris ||
                versionRange.equals(JAVA11) && element == Architecture.sparcv9 ||
                versionRange.equals(JAVA11) && element == ImageType.testimage
    }
}

