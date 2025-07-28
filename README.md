# Currency Exchange Bot 💱
## 🚀 Getting Started: Telegram Bot Setup Guide

### Prerequisites
1. **Telegram Account** - Create one at [telegram.org](https://telegram.org)
2. **IntelliJ IDEA** (2021.3+) - [Download here](https://www.jetbrains.com/idea/download)

### 🔑 Step 1: Get Your Bot Token
1. Open Telegram and search for **@BotFather**
2. Start a chat and use `/newbot` command
3. Follow instructions to:
    - Choose a bot name (e.g., `CurrencyExchangeBot`)
    - Pick a username (must end with `bot`, e.g., `currency_exchange_bot`)
4. Copy the generated token (looks like `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`)

### ⚙️ Step 2: Configure Environment
Create `.env` file in project root:
```ini
TELEGRAM_BOT_TOKEN=your_copied_token_here