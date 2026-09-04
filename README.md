# GPTPower-FlClash

Experimental modern-API LSPosed module for rooted Xiaomi fuxi / HyperOS 4. The `system_server` hook lets an existing VPN call pass unchanged. Otherwise it cancels the first `showSessionForActiveService()` call, cold-starts FlClash through `VPN_START_REQUESTED`, waits up to five seconds for a VPN transport, then replays the same active-service call with its original arguments. It stops FlClash when the assistant session hides only if this module started that VPN.

Enable the recommended scopes **System Framework**, `com.follow.clash`, and `com.openai.chatgpt`, then reboot. Keep ChatGPT as the default Assistant and use `power_button_long_press=5`.

This build targets API 37 and libxposed API 102. The HyperOS 4 method signatures still require fuxi device-log verification before the module should be treated as stable.
