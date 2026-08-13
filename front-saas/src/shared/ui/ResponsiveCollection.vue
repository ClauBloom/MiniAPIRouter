<template>
  <div class="responsive-collection">
    <div class="desktop">
      <slot name="table" />
    </div><div class="mobile">
      <slot name="cards" />
    </div>
  </div>
</template>
<style scoped>.mobile{display:none}@media(max-width:767px){.desktop{display:none}.mobile{display:block}}</style>
