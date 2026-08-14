-- 邮箱域名补全
-- 配合 rime_ice 自带的 recognizer email 规则使用（default.yaml 已内置）：
--   recognizer/patterns/email: "^[A-Za-z][-_.0-9A-Za-z]*@.*$"
-- 在 rime_ice.custom.yaml 中启用：
--   patch:
--     engine/translators/@next: lua_translator@*email
-- 输入 abc@ 后，候选栏列出常用邮箱域名，选中后补全为 abc@域名。

local M = {}

-- 常用邮箱域名（权重从高到低，可自行增删）
local domains = {
  "gmail.com", "qq.com", "163.com", "126.com", "outlook.com",
  "hotmail.com", "foxmail.com", "icloud.com", "yahoo.com",
  "sina.com", "aliyun.com", "googlemail.com", "yahoo.com.cn",
  "tom.com", "msn.com", "me.com", "zoho.com", "proton.me",
  "protonmail.com", "aol.com", "gmx.com", "mail.ru",
}

local function last_at(input)
  local pos
  local from = 1
  while true do
    local p = input:find("@", from, true)
    if not p then break end
    pos, from = p, p + 1
  end
  return pos
end

function M.init(env) end

function M.func(input, seg, env)
  local at = last_at(input)
  if not at then return end
  local local_part = input:sub(1, at - 1)
  if local_part == "" then return end
  local prefix = input:sub(at + 1)
  local n = 0
  for _, domain in ipairs(domains) do
    if domain:sub(1, #prefix) == prefix then
      local cand = Candidate("email", seg.start, seg._end,
                             local_part .. "@" .. domain, domain)
      cand.quality = 100 - n
      yield(cand)
      n = n + 1
      if n >= 9 then break end
    end
  end
end

return M
