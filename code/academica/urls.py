from django.urls import path
from . import views

app_name = "academica"

urlpatterns = [
    path("", views.lista, name="lista"),
    path("nuevo/", views.nuevo, name="nuevo"),
    path("<int:id>/", views.detalle, name="detalle"),
    path("<int:id>/editar/", views.editar, name="editar"),
]