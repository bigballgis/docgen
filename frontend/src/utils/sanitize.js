import DOMPurify from 'dompurify'

const ALLOWED_TAGS = ['p', 'br', 'span', 'div', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'ul', 'ol', 'li', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
  'strong', 'em', 'u', 's', 'sub', 'sup', 'blockquote', 'pre', 'code',
  'a', 'img', 'hr']

const ALLOWED_ATTR = ['href', 'src', 'alt', 'title', 'class', 'style',
  'colspan', 'rowspan', 'target', 'rel']

const ALLOWED_URI_REGEXP = /^(?:(?:(?:f|ht)tps?|mailto|tel|cid|xmpp):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i

export function sanitizeHtml(html) {
  if (!html) return ''
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS,
    ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false,
    ALLOWED_URI_REGEXP
  })
}
