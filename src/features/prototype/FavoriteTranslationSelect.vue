<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from '@/i18n'
import { readerControlMessages } from '@/i18n/readerControls'
import { useFavoriteTranslations } from '@/profile/favoriteTranslations'
import type { TranslationSummary } from '@/api/contracts'
const props = defineProps<{translations: TranslationSummary[]; selected: string}>()
const emit = defineEmits<{select:[string]}>()
const { language } = useI18n(), text = computed(() => readerControlMessages[language.value])
const { favorites, initialize, toggle } = useFavoriteTranslations(); initialize()
const all = ref(false), query = ref('')
const visible = computed(() => props.translations.filter(t => (all.value || !favorites.value.some(c => props.translations.some(t => t.code === c)) || favorites.value.includes(t.code)) && `${t.name} ${t.short_name || ''} ${t.language?.name || ''} ${t.code}`.toLocaleLowerCase().includes(query.value.toLocaleLowerCase())).sort((a,b) => Number(favorites.value.includes(b.code))-Number(favorites.value.includes(a.code)) || (a.language?.code || '').localeCompare(b.language?.code || '') || a.name.localeCompare(b.name)))
</script>
<template><section data-no-reader-gesture><button @click="all=!all">{{ all ? text.favorites : text.all }}</button><input v-if="all" v-model="query" type="search" :aria-label="text.all"/><ul class="favorite-translations"><li v-for="item in visible" :key="item.code"><button :aria-pressed="selected===item.code" @click="emit('select',item.code)">{{ item.name }} · {{ item.short_name || item.code }} <small>{{ item.language?.native_name || item.language?.name }}</small></button><button :aria-label="favorites.includes(item.code) ? text.unfavorite : text.favorite" :aria-pressed="favorites.includes(item.code)" @click="toggle(item.code)">{{ favorites.includes(item.code) ? '★' : '☆' }}</button></li></ul></section></template>
<style scoped>.favorite-translations{list-style:none;padding:0;max-height:20rem;overflow:auto}.favorite-translations li{display:flex;gap:.5rem;margin:.4rem 0}.favorite-translations li>button:first-child{flex:1;text-align:left}.favorite-translations small{display:block}</style>
