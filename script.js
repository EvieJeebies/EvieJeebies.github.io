// script.js
document.addEventListener('DOMContentLoaded', function () {
    const galleryItems = document.querySelectorAll('.gallery-item');
  
    galleryItems.forEach(item => {
      item.addEventListener('click', () => {
        const imgSrc = item.querySelector('img').src;
  
        // Create a lightbox
        const lightbox = document.createElement('div');
        lightbox.classList.add('lightbox');
        lightbox.innerHTML = `
          <div class="lightbox-content">
            <img src="${imgSrc}" alt="Gallery image">
            <span class="close">&times;</span>
          </div>
        `;
  
        document.body.appendChild(lightbox);
  
        // Close lightbox with a single handler
        const handleClose = (e) => {
          // Close if clicking anything except the image itself
          if (!e.target.matches('.lightbox-content img')) {
            lightbox.remove();
            document.removeEventListener('click', handleClose);
          }
        };

        // Add the click event listener after a brief delay to prevent immediate closing
        setTimeout(() => {
          document.addEventListener('click', handleClose);
        }, 50);
      });
    });
  });