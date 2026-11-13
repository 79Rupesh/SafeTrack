package com.safetrack.auth

import com.google.firebase.auth.PhoneAuthProvider

object PhoneAuthTokenHolder {
    var resendingToken: PhoneAuthProvider.ForceResendingToken? = null
}
