// SPDX-FileCopyrightText: 2026 YouTubely contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package app.youtubely.core.provider.newpipe

import app.youtubely.core.provider.ProviderError
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import java.io.IOException

/** Maps NewPipeExtractor failures to [ProviderError]. Order matters: specific subclasses first. */
internal fun Throwable.toProviderError(): ProviderError =
    when (this) {
        is ProviderError -> {
            this
        }

        is ReCaptchaException, is SignInConfirmNotBotException -> {
            ProviderError.RateLimited(this)
        }

        is GeographicRestrictionException, is UnsupportedContentInCountryException -> {
            ProviderError.GeoBlocked(this)
        }

        is AgeRestrictedContentException -> {
            ProviderError.AgeRestricted(this)
        }

        is PaidContentException, is YoutubeMusicPremiumContentException -> {
            ProviderError.Paid(this)
        }

        is PrivateContentException, is AccountTerminatedException, is ContentNotAvailableException -> {
            ProviderError.Removed(this)
        }

        is ParsingException -> {
            ProviderError.ExtractorOutdated(this)
        }

        is IOException -> {
            ProviderError.Network(this)
        }

        else -> {
            ProviderError.Unknown(this)
        }
    }
