import asyncio
from contextlib import ExitStack
import os,re
import random
import sys,json
from telegram import Bot,InputMediaDocument
from telegram.error import RetryAfter
from telegram.constants import ParseMode

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
MESSAGE_THREAD_ID = os.environ.get("MESSAGE_THREAD_ID")
DEVELOPING_THREAD_ID = os.environ.get("DEVELOPING_THREAD_ID")
RUN_URL = os.environ.get("RUN_URL")
TITLE = os.environ.get("TITLE")
VERSION = os.environ.get("VERSION")
BRANCH = os.environ.get("BRANCH")

API_ID = 21724
API_HASH = "3e0cb5efcd52300aec5994fdfc5bdc16"

GITHUB_EVENT = json.loads(open(os.environ.get("GITHUB_EVENT_PATH"), "r").read())
GITHUB_REF_TYPE = os.environ.get("GITHUB_REF_TYPE")

commit_message = ''
commit_line = ''

def format_commit_messages(commits, max_length=600):
    formatted_commits = []
    commits_to_show = commits[::-1]

    for index, commit in enumerate(commits_to_show):
        message = commit['message'].strip()
        if len(message) > 200:
            message = message[:197] + '...'
        message += ' by ' + commit['author']['username']

        remaining = len(commits_to_show) - index - 1
        candidate_commits = formatted_commits + [message]
        candidate_message = '\n------\n'.join(candidate_commits)
        if remaining:
            candidate_message += f'\n(other {remaining} commits)'

        if len(candidate_message) > max_length:
            omitted_count = len(commits_to_show) - index
            omitted_message = f'(other {omitted_count} commits)'
            commit_message = '\n------\n'.join(formatted_commits)
            if commit_message:
                commit_message += '\n'
            return commit_message + omitted_message

        formatted_commits.append(message)

    return '\n------\n'.join(formatted_commits)

try:
    if 'commits' in GITHUB_EVENT:
        commits = GITHUB_EVENT['commits']
        commit_message = format_commit_messages(commits)
    elif 'head_commit' in GITHUB_EVENT:
        msg = GITHUB_EVENT["head_commit"]["message"]
        if len(msg) > 200:
            msg = msg[:197] + '...'
        commit_message = f'{msg.strip()}'
    else:
        commit_message = f'(no commit message)'
except IndexError:
    print("[!] IndexError Found! try another way to get commit...")
    try:
        if 'head_commit' in GITHUB_EVENT:
            msg = GITHUB_EVENT["head_commit"]["message"]
            if len(msg) > 200:
                msg = msg[:197] + '...'
            commit_message = f'{msg.strip()}'
    except:
        from traceback import print_exc
        print_exc()
        commit_message = f'(no commit message)'
except:
    from traceback import print_exc
    print_exc()
    commit_message = f'(Unexpected Error! So no commit message)'

def escape_markdown_v2(text: str) -> str:
    return re.sub(r'([_*\[\]()~`>#+\-=|{}.!\\])', r'\\\1', text)

def escape_markdown_code(text: str) -> str:
    return text.replace('\\', '\\\\').replace('`', '\\`')

def escape_markdown_url(url: str) -> str:
    return url.replace('\\', '\\\\').replace(')', '\\)')

if 'compare' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['compare']
    commit_line = '[Compare](' + escape_markdown_url(commit_url) + ')'
elif 'head_commit' in GITHUB_EVENT:
    commit_url = GITHUB_EVENT['head_commit']['url']
    commit_line = '[Commit](' + escape_markdown_url(commit_url) + ')'
else:
    commit_line = ''

MSG_TEMPLATE = """
*{title}*
Branch: {branch}
\\#ci\\_{version}
```
{commit_message}
```
{commit_line}
[Workflow run]({run_url})
[Mobile Debug](https://nightly.link/Baka-SU/BakaSU/workflows/build-manager/main/Manager-mobile-debug.zip)
[Wear Debug](https://nightly.link/Baka-SU/BakaSU/workflows/build-manager/main/Manager-wear-debug.zip)
""".strip()

MAIN_UPDATED_MSG ="""
main branch updated, manager in there may outdated 
main 分支已更新，此 topic 的管理器可能已过时
""".strip()

async def start_bot_api():
    print("[+] Starting Telegram Bot API")
    import subprocess
    global process
    process = subprocess.Popen(["./telegram-bot-api-binary", f"--api-id={API_ID}", f"--api-hash={API_HASH}", "--local"])
    

async def wait_for_bot_api():
    import httpx
    await start_bot_api()
    print("[+] Waiting for Telegram Bot API to start...")
    async with httpx.AsyncClient() as client:
        for count in range(30):
            try:
                response = await client.get(f"http://127.0.0.1:8081/bot{BOT_TOKEN}/getMe")
                if response.status_code < 500:
                    print("[+] Telegram Bot API started")
                    break
            except:
                #print(process.stdout.readline())
                print("Still wait..." + str(count))
                await asyncio.sleep(1)
                pass
        else:
            process.kill()
            print("[-] Failed to start Telegram Bot API")
            exit(1)
        return 
        

def get_caption(flavor):
    msg = MSG_TEMPLATE.format(
        title=escape_markdown_v2(f"{TITLE} - {MANAGER_FLAVORS[flavor]}"),
        branch=escape_markdown_v2(BRANCH),
        version=escape_markdown_v2(VERSION),
        commit_message=escape_markdown_code(commit_message),
        commit_line=commit_line,
        run_url=escape_markdown_url(RUN_URL),
    )
    if BRANCH != "main" and GITHUB_REF_TYPE != "tag":
        msg += "\n⚠️⚠️*DEV VERSION, PLEASE BACKUP BEFORE INSTALLATION*⚠️⚠️"
        msg += "\n⚠️⚠️*测试版，安装前请备份*⚠️⚠️"
    return msg

def check_environ():
    global CHAT_ID, MESSAGE_THREAD_ID, DEVELOPING_THREAD_ID
    if BOT_TOKEN is None:
        print("[-] Invalid BOT_TOKEN")
        exit(1)
    if CHAT_ID is None:
        print("[-] Invalid CHAT_ID")
        exit(1)
    else:
        try:
            CHAT_ID = int(CHAT_ID)
        except:
            pass
    if RUN_URL is None:
        print("[-] Invalid RUN_URL")
        exit(1)
    if TITLE is None:
        print("[-] Invalid TITLE")
        exit(1)
    if VERSION is None:
        print("[-] Invalid VERSION")
        exit(1)
    if BRANCH is None:
        print("[-] Invalid BRANCH")
        exit(1)
    if MESSAGE_THREAD_ID and MESSAGE_THREAD_ID != "":
        try:
            MESSAGE_THREAD_ID = int(MESSAGE_THREAD_ID)
        except:
            print("[-] Invalid MESSAGE_THREAD_ID")
            exit(1)
    else:
        MESSAGE_THREAD_ID = None
    if DEVELOPING_THREAD_ID and DEVELOPING_THREAD_ID != "":
        try:
            DEVELOPING_THREAD_ID = int(DEVELOPING_THREAD_ID)
        except:
            print("[-] Invalid DEVELOPING_THREAD_ID")
            exit(1)
    else:
        DEVELOPING_THREAD_ID = None
        
async def send_message(bot: Bot, chat_id: int, text: str, message_thread_id=None):
    try:
        await asyncio.sleep(random.uniform(0.2, 0.8))
        return await bot.send_message(chat_id=chat_id, text=text, parse_mode=ParseMode.MARKDOWN_V2, message_thread_id=message_thread_id,
                                       read_timeout=350,write_timeout=350,connect_timeout=350,pool_timeout=350,disable_web_page_preview=True)
    except RetryAfter as e:
        print(f"[-] Hit Telegram flood limit, retrying after {e.retry_after} seconds...")
        await asyncio.sleep(e.retry_after)
        return await send_message(bot, chat_id, text, message_thread_id)
    except:
        raise

async def send_media_group(bot: Bot, chat_id: int, media: list, message_thread_id=None):
    try:
        await asyncio.sleep(random.uniform(0.2, 0.8))
        return await bot.send_media_group(chat_id=chat_id, media=media, message_thread_id=message_thread_id,
                                       read_timeout=350,write_timeout=350,connect_timeout=350,pool_timeout=350)
    except RetryAfter as e:
        print(f"[-] Hit Telegram flood limit, retrying after {e.retry_after} seconds...")
        await asyncio.sleep(e.retry_after)
        return await send_media_group(bot, chat_id, media, message_thread_id)
    except:
        raise

MANAGER_FLAVORS = {"mobile": "Phone / 手机 mobile", "wear": "Watch / 手表 wear"}
MANAGER_APK = re.compile(
    r"^BakaSU_(.+)_(\d+)-(mobile|wear)-(arm64-v8a|armeabi-v7a|x86_64|riscv64|universal)-release\.apk$"
)


def group_release_files(files):
    groups = {flavor: [] for flavor in MANAGER_FLAVORS}
    for file in sorted(files):
        name = os.path.basename(file)
        if name.endswith("-debug.apk"):
            continue
        match = MANAGER_APK.fullmatch(name)
        if match is None:
            raise ValueError(f"Unrecognized Manager release APK: {name}")
        if not os.path.isfile(file):
            raise FileNotFoundError(file)
        groups[match.group(3)].append(file)
    missing = [flavor for flavor, paths in groups.items() if not paths]
    if missing:
        raise ValueError(f"Missing Manager release APKs: {', '.join(missing)}")
    return groups


async def send_document(bot, chat_id, document, filename, caption, message_thread_id=None):
    try:
        return await bot.send_document(
            chat_id=chat_id, document=document, filename=filename, caption=caption,
            parse_mode=ParseMode.MARKDOWN_V2, message_thread_id=message_thread_id,
            read_timeout=350, write_timeout=350, connect_timeout=350, pool_timeout=350,
        )
    except RetryAfter as e:
        await asyncio.sleep(e.retry_after)
        document.seek(0)
        return await send_document(bot, chat_id, document, filename, caption, message_thread_id)


async def send_release_files(bot, flavor, files):
    caption = get_caption(flavor)
    if len(caption) > 1024:
        await send_message(bot, CHAT_ID, caption, MESSAGE_THREAD_ID)
        caption = "*" + escape_markdown_v2(f"Release Manager - {MANAGER_FLAVORS[flavor]}") + "*"
    # Telegram albums accept 2-10 documents; send a remaining single file separately.
    for start in range(0, len(files), 10):
        batch = files[start:start + 10]
        with ExitStack() as stack:
            documents = [stack.enter_context(open(file, "rb")) for file in batch]
            if len(batch) == 1:
                await send_document(bot, CHAT_ID, documents[0], os.path.basename(batch[0]), caption, MESSAGE_THREAD_ID)
            else:
                media = [
                    InputMediaDocument(
                        media=document, filename=os.path.basename(file),
                        caption=caption if index == len(batch) - 1 else None,
                        parse_mode=ParseMode.MARKDOWN_V2,
                    )
                    for index, (file, document) in enumerate(zip(batch, documents))
                ]
                await send_media_group(bot, CHAT_ID, media, MESSAGE_THREAD_ID)


async def main():
    print("[+] Uploading to telegram")
    check_environ()
    groups = group_release_files(sys.argv[1:])
    print("[+] Files:", groups)
    await wait_for_bot_api()
    bot = Bot(token=BOT_TOKEN, base_url="http://127.0.0.1:8081/bot", base_file_url="http://127.0.0.1:8081/file/bot")
    for flavor, files in groups.items():
        await send_release_files(bot, flavor, files)
    if TITLE.lower() == "manager" and (BRANCH == "main" or GITHUB_REF_TYPE == "tag"):
        print("[+] Sending main branch updated message")
        await send_message(bot=bot,chat_id=CHAT_ID, text=MAIN_UPDATED_MSG, message_thread_id=DEVELOPING_THREAD_ID)
    print("[+] Done!")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"[-] An error occurred: {e}")
        raise
    finally:
        try:
            process.kill()
        except:
            pass
